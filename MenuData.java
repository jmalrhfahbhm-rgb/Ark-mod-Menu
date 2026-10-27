package com.fstudio.arkmenu;

import java.util.*;

/** All menu content lives here as data. Each entry is {label, console command}.
 *  Placeholders: {LVL} = level box, {QTY} = quantity box. */
public final class MenuData {
    private MenuData() {}

    public static final String[] TABS = {"Player", "Dinos", "Items", "World", "Teleport", "Console"};

    // ---------- Player ----------
    public static final String[][] PLAYER = {
        {"God Mode",            "cheat God"},
        {"Infinite Stats",      "cheat InfiniteStats"},      // stamina, food, water, oxygen
        {"Fly",                 "cheat Fly"},
        {"Walk (stop fly)",     "cheat Walk"},
        {"Ghost (noclip)",      "cheat Ghost"},
        {"Wild dinos ignore me","cheat LeaveMeAlone"},
        {"One-Hit Kill",        "cheat SetPlayerDamageMultiplier 1000"}, // verify on your build
        {"Speed x3",            "cheat SetPlayerSpeedMultiplier 3"},     // verify on your build
        {"Max level",           "cheat GiveExpToPlayer 99999999 0 0"},
        {"Unlock all Engrams",  "cheat GiveEngrams"},                    // verify on your build
    };

    // ---------- Dinos: {label, blueprint class} ----------
    public static final String[][] DINOS = {
        {"Rex",            "Rex_Character_BP_C"},
        {"Giganotosaurus", "Gigant_Character_BP_C"},
        {"Spinosaurus",    "Spino_Character_BP_C"},
        {"Griffin",        "Griffin_Character_BP_C"},
        {"Pteranodon",     "Ptero_Character_BP_C"},
        {"Wyvern (Fire)",  "Wyvern_Character_BP_Fire_C"},
        {"Argentavis",     "Argent_Character_BP_C"},
        {"Quetzal",        "Quetz_Character_BP_C"},
        {"Raptor",         "Raptor_Character_BP_C"},
        {"Triceratops",    "Trike_Character_BP_C"},
        {"Mosasaurus",     "Mosa_Character_BP_C"},
        {"Therizinosaur",  "Therizinosaurus_Character_BP_C"},
        // Note: Pteranodon's class is Ptero_Character_BP_C (not "Ptera"). Verify the rest the same way.
    };
    // Summon first, tame second. Order matters.
    public static final String[] SPAWN_TAMED = {
        "cheat GMSummon \"{CLASS}\" {LVL}",
        "cheat DoTame"
    };
    public static final String[][] DINO_ACTIONS = {
        {"Heal target dino", "cheat HealMyTarget"},    // verify on your build
        {"Tame target",      "cheat DoTame"},
        {"+10 level target", "cheat AddDinoLevel 10"}, // verify on your build
        {"Kill nearby wild", "cheat DestroyWildDinos"},
    };

    // ---------- Items ----------
    // Replace 0 with real item IDs / blueprint paths from a current item list for your version.
    public static final String[][] ITEMS = {
        {"Wood x{QTY}", "cheat GiveItem \"Blueprint'/Game/PrimalEarth/CoreBlueprints/Resources/PrimalItemResource_Wood.PrimalItemResource_Wood'\" {QTY} 0 0"},
        {"Stone x{QTY}", "cheat GiveItem \"Blueprint'/Game/PrimalEarth/CoreBlueprints/Resources/PrimalItemResource_Stone.PrimalItemResource_Stone'\" {QTY} 0 0"},
        {"Metal x{QTY}", "cheat GiveItem \"Blueprint'/Game/PrimalEarth/CoreBlueprints/Resources/PrimalItemResource_Metal.PrimalItemResource_Metal'\" {QTY} 0 0"},
        {"Metal Ingot x{QTY}", "cheat GiveItem \"Blueprint'/Game/PrimalEarth/CoreBlueprints/Resources/PrimalItemResource_MetalIngot.PrimalItemResource_MetalIngot'\" {QTY} 0 0"},
        {"Wool x{QTY}", "cheat GiveItem \"Blueprint'/Game/PrimalEarth/CoreBlueprints/Resources/PrimalItemResource_Wool.PrimalItemResource_Wool'\" {QTY} 0 0"},
        {"Element x{QTY}", "cheat GiveItem \"Blueprint'/Game/PrimalEarth/CoreBlueprints/Resources/PrimalItemResource_Element.PrimalItemResource_Element'\" {QTY} 0 0"},
        {"Element Shard x{QTY}", "cheat GiveItem \"Blueprint'/Game/PrimalEarth/CoreBlueprints/Resources/PrimalItemResource_ElementShard.PrimalItemResource_ElementShard'\" {QTY} 0 0"},
        // Resource paths above are the standard base-game ones; verify against your build.
        // There is no "Tek wool" item. Tek weapons/armor need their own paths: add them here once verified.
    };
    public static final String[][] ITEM_ACTIONS = {
        {"Give all Blueprints", "cheat GiveAllStructure"}, // verify on your build
    };

    // ---------- World ----------
    public static final String[][] WORLD = {
        {"Noon",           "cheat SetTimeOfDay 12:00"},
        {"Midnight",       "cheat SetTimeOfDay 00:00"},
        {"Time x10",       "cheat Slomo 10"},
        {"Time normal",    "cheat Slomo 1"},
        {"Clear weather",  "cheat SetClearWeather"},      // verify on your build
        {"Force Save",     "cheat SaveWorld"},
    };

    // ---------- Teleport: {label, x, y, z} ---- fill with real map coordinates
    public static final String[][] TELEPORTS = {
        // {"Swamp Cave", "x", "y", "z"},
    };
    public static String tpCmd(String x, String y, String z) {
        return "cheat SetPlayerPos " + x + " " + y + " " + z;
    }

    public static String fill(String cmd, Map<String,String> vars) {
        for (Map.Entry<String,String> e : vars.entrySet())
            cmd = cmd.replace("{" + e.getKey() + "}", e.getValue());
        return cmd;
    }
}
