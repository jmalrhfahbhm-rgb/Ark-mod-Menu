// arkmenu.cpp - JNI bridge + command queue + runtime pattern scanner.
// Loaded INSIDE the game process. At load, a worker thread waits for libUE4.so, scans its
// executable memory for two byte patterns, and installs one hook (via Dobby).
//
// IMPORTANT: the two patterns below are intentionally empty. I do not have verified byte
// signatures for your Ark Mobile build and will not invent them (a wrong pattern = crash or a
// hook in the wrong place). dlsym cannot be used either: shipping UE4 builds strip these symbols,
// so dlsym("ProcessConsoleExec") returns null. Get the signatures from your own libUE4.so
// (disassembler: find the "ProcessConsoleExec"/console-command string xref, take ~16-24 bytes of
// the function prologue, mask relocatable bytes with ??). ARM64 example format: "FD 7B BF A9 ?? ?? 00 91".

#include <jni.h>
#include <android/log.h>
#include <string>
#include <queue>
#include <mutex>
#include <atomic>
#include <thread>
#include <chrono>
#include <vector>
#include <fstream>
#include <sstream>
#include <cstdint>
#include <cstring>

#define TAG "ArkMenu"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// ---- Dobby (https://github.com/jmpews/Dobby) is a third-party hook library you add to the build.
extern "C" int DobbyHook(void* address, void* replace, void** origin);

// ======================= FILL THESE IN =======================
static const char* LIB_NAME            = "libUE4.so";
static const char* PATTERN_CONSOLE_EXEC = "";   // function that executes a console command string
static const char* PATTERN_PLAYER_TICK  = "";   // per-frame game-thread function: void f(void* playerController, float dt)
// =============================================================

namespace {
    std::mutex g_mx;
    std::queue<std::string> g_queue;
    std::atomic<bool> g_ready{false};

    // Assumed UE4 signature; verify TCHAR width on your build (char16_t is typical on Android).
    using ConsoleExec_t = bool (*)(void* self, const char16_t* cmd, void* outputDevice, void* executor);
    using PlayerTick_t  = void (*)(void* self, float dt);
    ConsoleExec_t g_exec = nullptr;
    PlayerTick_t  g_origTick = nullptr;

    struct Range { uintptr_t begin, end; };

    // Collect every executable mapping of the module from /proc/self/maps.
    std::vector<Range> exec_ranges(const char* lib) {
        std::vector<Range> out; std::ifstream f("/proc/self/maps"); std::string line;
        while (std::getline(f, line)) {
            if (line.find(lib) == std::string::npos) continue;
            uintptr_t a, b; char perms[5] = {0};
            if (sscanf(line.c_str(), "%lx-%lx %4s", &a, &b, perms) == 3 && perms[0]=='r' && perms[2]=='x')
                out.push_back({a, b});
        }
        return out;
    }

    // Parse "AA BB ?? CC" into bytes + mask.
    bool parse(const char* pat, std::vector<uint8_t>& bytes, std::vector<bool>& mask) {
        std::istringstream ss(pat); std::string t;
        while (ss >> t) {
            if (t == "??" || t == "?") { bytes.push_back(0); mask.push_back(false); }
            else { bytes.push_back((uint8_t)strtoul(t.c_str(), nullptr, 16)); mask.push_back(true); }
        }
        return !bytes.empty();
    }

    uintptr_t find(const std::vector<Range>& rs, const char* pat) {
        std::vector<uint8_t> b; std::vector<bool> m;
        if (!pat || !*pat || !parse(pat, b, m)) return 0;
        for (const Range& r : rs) {
            const uint8_t* p = (const uint8_t*)r.begin; size_t n = r.end - r.begin;
            for (size_t i = 0; i + b.size() <= n; i += 4 /* ARM64 instructions are 4-byte aligned */) {
                size_t j = 0; while (j < b.size() && (!m[j] || p[i+j] == b[j])) ++j;
                if (j == b.size()) return r.begin + i;
            }
        }
        return 0;
    }

    std::u16string widen(const std::string& s) {      // ASCII only: enough for console commands
        std::u16string o; o.reserve(s.size());
        for (unsigned char c : s) o.push_back(c);
        return o;
    }

    // Runs on the game thread, once per frame.
    void hookedTick(void* pc, float dt) {
        if (g_origTick) g_origTick(pc, dt);
        if (!g_exec || !pc) return;
        std::queue<std::string> local;
        { std::lock_guard<std::mutex> lk(g_mx); std::swap(local, g_queue); }
        while (!local.empty()) {
            std::u16string w = widen(local.front()); local.pop();
            g_exec(pc, w.c_str(), nullptr /* engine GLog */, pc);
        }
    }

    void setup() {
        std::vector<Range> rs;
        for (int i = 0; i < 600 && rs.empty(); ++i) {                 // wait up to ~5 min for the engine lib
            rs = exec_ranges(LIB_NAME);
            if (rs.empty()) std::this_thread::sleep_for(std::chrono::milliseconds(500));
        }
        if (rs.empty()) { LOGI("%s not found", LIB_NAME); return; }
        uintptr_t exec = find(rs, PATTERN_CONSOLE_EXEC);
        uintptr_t tick = find(rs, PATTERN_PLAYER_TICK);
        if (!exec || !tick) { LOGI("patterns not set or not found (exec=%p tick=%p)", (void*)exec, (void*)tick); return; }
        g_exec = (ConsoleExec_t)exec;
        if (DobbyHook((void*)tick, (void*)hookedTick, (void**)&g_origTick) == 0) { g_ready = true; LOGI("hook installed"); }
        else LOGI("hook failed");
    }
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    std::thread(setup).detach();
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_fstudio_arkmenu_NativeBridge_execNative(JNIEnv* env, jclass, jstring jcmd) {
    const char* c = env->GetStringUTFChars(jcmd, nullptr);
    std::string cmd(c); env->ReleaseStringUTFChars(jcmd, c);
    if (cmd.empty() || cmd.size() > 512) return;
    std::lock_guard<std::mutex> lk(g_mx);
    if (g_queue.size() < 64) g_queue.push(std::move(cmd));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_fstudio_arkmenu_NativeBridge_isReadyNative(JNIEnv*, jclass) { return g_ready.load(); }
