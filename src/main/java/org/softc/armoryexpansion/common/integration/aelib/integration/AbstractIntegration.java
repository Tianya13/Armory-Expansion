package org.softc.armoryexpansion.common.integration.aelib.integration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.logging.log4j.Logger;
import org.softc.armoryexpansion.ArmoryExpansion;
import org.softc.armoryexpansion.common.integration.aelib.config.IntegrationConfig;
import org.softc.armoryexpansion.common.integration.aelib.config.MaterialConfigOptions;
import org.softc.armoryexpansion.common.integration.aelib.plugins.constructsarmory.material.ArmorToolMaterial;
import org.softc.armoryexpansion.common.integration.aelib.plugins.constructsarmory.material.ArmorToolRangedMaterial;
import org.softc.armoryexpansion.common.integration.aelib.plugins.general.material.IBasicMaterial;
import org.softc.armoryexpansion.common.integration.aelib.plugins.general.oredictionary.BasicOreDictionary;
import org.softc.armoryexpansion.common.integration.aelib.plugins.general.oredictionary.IOreDictionary;
import org.softc.armoryexpansion.common.integration.aelib.plugins.general.traits.MaterialTraits;
import org.softc.armoryexpansion.common.integration.aelib.plugins.tinkersconstruct.alloys.Alloy;
import org.softc.armoryexpansion.common.integration.aelib.plugins.tinkersconstruct.alloys.IAlloy;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractIntegration implements IIntegration {
    public static final String CONFIG_SUFFIX = "-config";
    public static final String ALLOYS_SUFFIX = "-alloys";
    public static final String ORE_DICT_ENTRIES_SUFFIX = "-oreDictEntries";
    public static final String MATERIALS_SUFFIX = "-materials";
    public static final String TRAITS_SUFFIX = "-traits";

    protected Logger logger;
    protected String modId = "";
    protected String root= "";
    protected String configDir;
    protected IntegrationConfig integrationConfigHelper = new IntegrationConfig();
    private boolean forceCreateJson;

    protected Map<String, IBasicMaterial> materials = new HashMap<>();
    protected Map<String, MaterialTraits> materialTraits = new HashMap<>();
    private final Map<String, IOreDictionary> oreDictionaryEntries = new HashMap<>();
    private final Map<String, IAlloy> alloys = new HashMap<>();

    protected AbstractIntegration() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    protected AbstractIntegration(String modId, String root) {
        this();
        this.modId = modId;
        this.root = root;
    }

    @Override
    public boolean isLoadable() {
        return Loader.isModLoaded(this.modId) && ArmoryExpansion.isIntegrationEnabled(this.modId);
    }

    // Forge Mod Loader events
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        this.logger = event.getModLog();
        this.configDir = event.getModConfigurationDirectory().getPath();
        if(this.isLoadable()){
            this.loadIntegrationData(this.configDir);
            this.integrationConfigHelper.syncConfig(this.materials);
            this.saveIntegrationData(this.configDir);
            this.registerMaterials();
            this.registerMaterialFluids();
//            this.registerMaterialStats();
        }
        ArmoryExpansion.getConfig().save();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        if(this.isLoadable()){
            this.oredictMaterials();
            this.registerMaterialFluidsIMC();
            this.updateMaterials();
            this.registerMaterialTraits();
        }
        ArmoryExpansion.getConfig().save();
    }

    @Override
    public void postInit(FMLPostInitializationEvent event){
        // Used as a stub
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void registerItems(RegistryEvent<Item> event){
        if(this.isLoadable()){
            this.registerMaterialStats();
//            this.registerMaterialFluids();
            this.registerAlloys();
        }
        ArmoryExpansion.getConfig().save();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @Override
    public void registerBlocks(RegistryEvent.Register<? super Block> event){
        if(ArmoryExpansion.isIntegrationEnabled(this.modId)) {
//            this.registerMaterialFluids();
            this.registerFluidBlocks(event);
        }
    }

    // Integration Data
    protected void loadIntegrationData(String path){
        this.loadConfig(path);
        this.loadMaterials(path);
        this.loadTraits(path);
        this.loadOreDictionaryEntries(path);
        this.loadAlloys(path);
    }

    protected void saveIntegrationData(String path){
        this.saveConfig(path);
        this.saveMaterials(path);
        this.saveTraits(path);
        this.saveOreDictionaryEntries(path);
        this.saveAlloys(path);
    }

    // Traits
    protected void loadTraits(String path) {
        this.loadTraitsFromJson(new File(path), this.modId);
        this.logger.info("Done loading all material traits from local JSON files");
        this.loadTraitsFromSource();
        this.logger.info("Done loading all material traits from source");
    }

    protected void addTraits(MaterialTraits material){
        if(this.isMaterialEnabled(material.getIdentifier())){
            this.materialTraits.putIfAbsent(material.getIdentifier(), material);
        }
    }

    protected void saveTraits(String path){
        this.saveTraitsToJson(new File(path), this.modId, this.forceCreateJson);
        this.logger.info("Done saving all material traits to local JSON files");
    }

    private void saveTraitsToJson(String path, boolean forceCreate){
        if(!this.materialTraits.values().isEmpty() || forceCreate) {
            Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();
            File output = new File(path);
            output.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(output)){
                writer.write(gson.toJson(this.materialTraits.values()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveTraitsToJson(File dir, String dirTree, String fileName, boolean forceCreate){
        this.saveTraitsToJson(dir.getPath() + "/" + dirTree + "/" + fileName + ".json", forceCreate);
    }

    private void saveTraitsToJson(File dir, String fileName, boolean forceCreate){
        this.saveTraitsToJson(dir, this.root, fileName + "/" + fileName + TRAITS_SUFFIX, forceCreate);
    }

    private void loadTraits(MaterialTraits[] jsonMaterials){
        if(null != jsonMaterials) {
            for (MaterialTraits material : jsonMaterials) {
                this.materialTraits.putIfAbsent(material.getIdentifier(), material);
            }
        }
    }

    void loadTraitsFromJson(InputStream path){
        Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();

        MaterialTraits[] jsonMaterials = gson.fromJson(
                new BufferedReader(
                        new InputStreamReader(
                                new BoundedInputStream(path, ArmoryExpansion.getBoundedInputStreamMaxSize()))), MaterialTraits[].class);
        this.loadTraits(jsonMaterials);
    }

    private void loadTraitsFromJson(String path){
        Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();

        MaterialTraits[] jsonMaterials = new MaterialTraits[0];
        try {
            File input = new File(path);
            if(input.exists()){
                jsonMaterials = gson.fromJson(new FileReader(input), MaterialTraits[].class);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        this.loadTraits(jsonMaterials);
    }

    private void loadTraitsFromJson(File dir, String dirTree, String fileName){
        this.loadTraitsFromJson(dir.getPath() + "/" + dirTree + "/" + fileName + ".json");
    }

    private void loadTraitsFromJson(File dir, String fileName){
        this.loadTraitsFromJson(dir, this.root, fileName + "/" + fileName + TRAITS_SUFFIX);
    }

    protected abstract void loadTraitsFromSource();

    // Materials
    protected void loadMaterials(String path){
        this.loadMaterialsFromJson(new File(path), this.modId);
        this.logger.info("Done loading all materials from local JSON files");
        this.loadMaterialsFromSource();
        this.logger.info("Done loading all materials from source");
    }

    protected void addMaterial(IBasicMaterial material){
        if(this.isMaterialEnabled(material.getIdentifier())){
            this.materials.putIfAbsent(material.getIdentifier(), material);
        }
    }

    protected void saveMaterials(String path){
        this.saveMaterialsToJson(new File(path), this.modId, this.forceCreateJson);
        this.logger.info("Done saving all materials to local JSON files");
    }

    private void saveMaterialsToJson(String path, boolean forceCreate){
        if(!this.materials.values().isEmpty() || forceCreate) {
            Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();
            File output = new File(path);
            output.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(output)){
                writer.write(gson.toJson(this.materials.values()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveMaterialsToJson(File dir, String dirTree, String fileName, boolean forceCreate){
        this.saveMaterialsToJson(dir.getPath() + "/" + dirTree + "/" + fileName + ".json", forceCreate);
    }

    private void saveMaterialsToJson(File dir, String fileName, boolean forceCreate){
        this.saveMaterialsToJson(dir, this.root, fileName + "/" + fileName + MATERIALS_SUFFIX, forceCreate);
    }

    private void loadMaterials(ArmorToolMaterial[] jsonMaterials){
        if(null != jsonMaterials) {
            for (ArmorToolMaterial material : jsonMaterials) {
                this.materials.putIfAbsent(material.getIdentifier(), material);
            }
        }
    }

    void loadMaterialsFromJson(InputStream path){
        Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();

        ArmorToolRangedMaterial[] jsonMaterials = gson.fromJson(
                new BufferedReader(
                        new InputStreamReader(
                                new BoundedInputStream(path, ArmoryExpansion.getBoundedInputStreamMaxSize()))), ArmorToolRangedMaterial[].class);
        this.loadMaterials(jsonMaterials);
    }

    private void loadMaterialsFromJson(String path){
        Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();

        ArmorToolRangedMaterial[] jsonMaterials = new ArmorToolRangedMaterial[0];
        try {
            File input = new File(path);
            if(input.exists()){
                jsonMaterials = gson.fromJson(new FileReader(input), ArmorToolRangedMaterial[].class);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        this.loadMaterials(jsonMaterials);
    }

    private void loadMaterialsFromJson(File dir, String dirTree, String fileName){
        this.loadMaterialsFromJson(dir.getPath() + "/" + dirTree + "/" + fileName + ".json");
    }

    private void loadMaterialsFromJson(File dir, String fileName){
        this.loadMaterialsFromJson(dir, this.root, fileName + "/" + fileName + MATERIALS_SUFFIX);
    }

    protected abstract void loadMaterialsFromSource();

    // Ore Dictionary Entries
    protected void loadOreDictionaryEntries(String path){
        this.loadOreDictionaryEntriesFromJson(new File(path), this.modId);
        this.logger.info("Done loading all ore dictionary entries from local JSON files");
        this.loadOreDictionaryEntriesFromSource();
        this.logger.info("Done loading all ore dictionary entries from source");
    }

    private void addOreDictionaryEntry(IOreDictionary oreDictionary) {
        if(this.isMaterialEnabled(oreDictionary.getIdentifier())){
            this.oreDictionaryEntries.putIfAbsent(oreDictionary.getIdentifier(), oreDictionary);
        }
    }

    protected void saveOreDictionaryEntries(String path) {
        this.saveOreDictionaryEntriesToJson(new File(path), this.modId, this.forceCreateJson);
        this.logger.info("Done saving all ore dictionary entries to local JSON files");
    }

    private void saveOreDictionaryEntriesToJson(String path, boolean forceCreate) {
        if(!this.oreDictionaryEntries.values().isEmpty() || forceCreate) {
            Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();
            File output = new File(path);
            output.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(output)) {
                writer.write(gson.toJson(this.oreDictionaryEntries.values()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveOreDictionaryEntriesToJson(File dir, String dirTree, String fileName, boolean forceCreate) {
        this.saveOreDictionaryEntriesToJson(dir.getPath() + "/" + dirTree + "/" + fileName + ".json", forceCreate);
    }

    private void saveOreDictionaryEntriesToJson(File dir, String fileName, boolean forceCreate) {
        this.saveOreDictionaryEntriesToJson(dir, this.root, fileName + "/" + fileName + ORE_DICT_ENTRIES_SUFFIX, forceCreate);
    }

    private void loadOreDictionaryEntries(IOreDictionary[] jsonOreDicts) {
        if(null != jsonOreDicts) {
            for (IOreDictionary iOreDictionary : jsonOreDicts) {
                this.oreDictionaryEntries.putIfAbsent(iOreDictionary.getIdentifier(), iOreDictionary);
            }
        }
    }

    void loadOreDictionaryEntriesFromJson(InputStream path) {
        Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();

        IOreDictionary[] jsonMaterials = gson.fromJson(
                new BufferedReader(
                        new InputStreamReader(
                                new BoundedInputStream(path, ArmoryExpansion.getBoundedInputStreamMaxSize()))), BasicOreDictionary[].class);
        this.loadOreDictionaryEntries(jsonMaterials);
    }

    private void loadOreDictionaryEntriesFromJson(String path) {
        Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();

        IOreDictionary[] jsonMaterials = new BasicOreDictionary[0];
        try {
            File input = new File(path);
            if(input.exists()){
                jsonMaterials = gson.fromJson(new FileReader(input), BasicOreDictionary[].class);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        this.loadOreDictionaryEntries(jsonMaterials);
    }

    private void loadOreDictionaryEntriesFromJson(File dir, String dirTree, String fileName) {
        this.loadOreDictionaryEntriesFromJson(dir.getPath() + "/" + dirTree + "/" + fileName + ".json");
    }

    private void loadOreDictionaryEntriesFromJson(File dir, String fileName) {
        this.loadOreDictionaryEntriesFromJson(dir, this.root, fileName + "/" + fileName + ORE_DICT_ENTRIES_SUFFIX);
    }

    protected abstract void loadOreDictionaryEntriesFromSource();

    // Alloys
    protected void loadAlloys(String path){
        this.loadAlloysFromJson(new File(path), this.modId);
        this.logger.info("Done loading all alloys from local JSON files");
        this.loadAlloysFromSource();
        this.logger.info("Done loading all alloys from source");
    }

    protected void saveAlloys(String path){
        this.saveAlloysToJson(new File(path), this.modId, this.forceCreateJson);
        this.logger.info("Done saving all alloys to local JSON files");
    }

    private void saveAlloysToJson(String path, boolean forceCreate){
        if(!this.alloys.values().isEmpty() || forceCreate) {
            Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();
            File output = new File(path);
            output.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(output)) {
                writer.write(this.returnAlloyExample());
                writer.write(gson.toJson(this.alloys.values()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveAlloysToJson(File dir, String dirTree, String fileName, boolean forceCreate){
        this.saveAlloysToJson(dir.getPath() + "/" + dirTree + "/" + fileName + ".json", forceCreate);
    }

    private void saveAlloysToJson(File dir, String fileName, boolean forceCreate){
        this.saveAlloysToJson(dir, this.root, fileName + "/" + fileName + ALLOYS_SUFFIX, forceCreate);
    }

    private void loadAlloys(Alloy[] jsonAlloys){
        if(null != jsonAlloys) {
            for (Alloy a : jsonAlloys) {
                this.alloys.putIfAbsent(a.getName(), a);
            }
        }
    }

    void loadAlloysFromJson(InputStream path){
        Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();

        Alloy[] jsonAlloys = new Alloy[0];
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            new BoundedInputStream(path, ArmoryExpansion.getBoundedInputStreamMaxSize())));
            jsonAlloys = gson.fromJson(reader, Alloy[].class);
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.loadAlloys(jsonAlloys);
    }

    private void loadAlloysFromJson(String path){
        Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();
        Alloy[] jsonAlloys = new Alloy[0];
        try {
            File input = new File(path);
            if(input.exists()){
                jsonAlloys = gson.fromJson(new FileReader(input), Alloy[].class);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        this.loadAlloys(jsonAlloys);
    }

    private void loadAlloysFromJson(File dir, String dirTree, String fileName){
        this.loadAlloysFromJson(dir.getPath() + "/" + dirTree + "/" + fileName + ".json");
    }

    private void loadAlloysFromJson(File dir, String fileName){
        this.loadAlloysFromJson(dir, this.root, fileName + "/" + fileName + ALLOYS_SUFFIX);
    }

    protected abstract void loadAlloysFromSource();

    // Config
    protected void loadConfig(String path){
        this.loadConfigFromJson(new File(path), this.modId);
        this.logger.info("Done loading config from local JSON file");
        this.loadConfigFromSource();
        this.logger.info("Done loading config from source");
    }

    private void saveConfig(String path){
        this.saveConfigToJson(new File(path), this.modId, this.forceCreateJson);
        this.logger.info("Done saving config to local JSON file");
    }

    private void saveConfigToJson(String path, boolean forceCreate){
        if(!this.materials.values().isEmpty() || forceCreate) {
            Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();
            File output = new File(path);
            output.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(output)) {
                writer.write(gson.toJson(this.integrationConfigHelper.getIntegrationMaterials().values().toArray()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveConfigToJson(File dir, String dirTree, String fileName, boolean forceCreate){
        this.saveConfigToJson(dir.getPath() + "/" + dirTree + "/" + fileName + ".json", forceCreate);
    }

    private void saveConfigToJson(File dir, String fileName, boolean forceCreate){
        this.saveConfigToJson(dir, this.root, fileName + "/" + fileName + CONFIG_SUFFIX, forceCreate);
    }

    private void loadConfig(MaterialConfigOptions[] materialConfig){
        if(null != materialConfig){
            if (null == this.integrationConfigHelper){
                this.integrationConfigHelper = new IntegrationConfig();
            }
            for(MaterialConfigOptions material : materialConfig){
                this.integrationConfigHelper.insertMaterialConfigOptions(material);
            }
        }
    }

    void loadConfigFromJson(InputStream path){
        Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();

        MaterialConfigOptions[] jsonConfig = gson.fromJson(
                new BufferedReader(
                        new InputStreamReader(
                                new BoundedInputStream(path, ArmoryExpansion.getBoundedInputStreamMaxSize()))), MaterialConfigOptions[].class);
        this.loadConfig(jsonConfig);
    }

    private void loadConfigFromJson(String path){
        Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();

        MaterialConfigOptions[] jsonConfig = new MaterialConfigOptions[0];
        try {
            File input = new File(path);
            if(input.exists()){
                jsonConfig = gson.fromJson(new FileReader(input), MaterialConfigOptions[].class);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        this.loadConfig(jsonConfig);
    }

    private void loadConfigFromJson(File dir, String dirTree, String fileName){
        this.loadConfigFromJson(dir.getPath() + "/" + dirTree + "/" + fileName + ".json");
    }

    private void loadConfigFromJson(File dir, String fileName){
        this.loadConfigFromJson(dir, this.root, fileName + "/" + fileName + CONFIG_SUFFIX);
    }

    protected abstract void loadConfigFromSource();

    // IIntegration implementations
    @Override
    public void oredictMaterials() {
        this.materials.values().forEach(material -> {
            if (this.isMaterialEnabled(material.getIdentifier())){
                IOreDictionary oreDictionary = this.oreDictionaryEntries.get(material.getIdentifier());
                if (null != oreDictionary)
                    oreDictionary.registerOreDict();
                this.logger.info("Oredicted material {" + material.getIdentifier() + "};");
            }
        });
    }

    @Override
    public void registerMaterials() {
        this.materials.values().forEach(material -> {
            if (material.registerTinkersMaterial(this.isMaterialEnabled(material.getIdentifier()))) {
                this.logger.info("Registered tinker's material {" + material.getIdentifier() + "};");
            }
        });
    }

    @Override
    public void registerMaterialFluids() {
        this.materials.values().forEach(material -> {
            if (material.registerTinkersFluid(this.isMaterialEnabled(material.getIdentifier()) && this.isMaterialFluidEnabled(material.getIdentifier()))) {
                this.logger.info("Registered fluid for material {" + material.getIdentifier() + "};");
            }
        });
    }

    @Override
    public void registerMaterialFluidsIMC(){
        this.materials.values().forEach(material -> {
            if (material.registerTinkersFluidIMC(this.isMaterialEnabled(material.getIdentifier()) && this.isMaterialFluidEnabled(material.getIdentifier()))) {
                this.logger.info("Sent IMC for tinker's fluid {" + material.getFluidName() + "};");
            }
        });
    }

    @Override
    public void registerFluidBlocks(RegistryEvent.Register<? super Block> event){
        this.materials.values().forEach(material -> {
            if(material.isCastable()){
                // TODO Fix this!!
                event.getRegistry().register(material.getFluidBlock());
                this.logger.info("Registered fluid block for material {" + material.getIdentifier() + "};");
            }
        });
    }

    @Override
    public void registerAlloys(){
        this.alloys.values().forEach(a -> {
            a.registerTiCAlloy();
            this.logger.info("Sent IMC for tinker's alloy {" + a.getName() + "};");
        });
    }

    @Override
    public void registerMaterialStats() {
        this.materials.values().forEach(material -> {
            if (material.registerTinkersMaterialStats(this.getProperties(material))) {
                this.logger.info("Registered stats for tinker's material {" + material.getIdentifier() + "};");
            }
        });
    }

    @Override
    public void updateMaterials() {
        this.materials.values().forEach(material -> {
            IOreDictionary oreDictionaryEntry = this.oreDictionaryEntries.get(material.getIdentifier());
            if (null != oreDictionaryEntry && oreDictionaryEntry.updateTinkersMaterial(this.isMaterialEnabled(material.getIdentifier()))) {
                this.logger.info("Updated tinker's material {" + material.getIdentifier() + "};");
            }
        });
    }

    @Override
    public void registerMaterialTraits() {
        this.materialTraits.values().forEach(materialTraits -> {
            if (materialTraits.registerTinkersMaterialTraits(this.isMaterialEnabled(materialTraits.getIdentifier()) && this.integrationConfigHelper.traitsEnabled(materialTraits.getIdentifier()))) {
                this.logger.info("Registered traits for tinker's materialTraits {" + materialTraits.getIdentifier() + "};");
            }
        });
    }

    @Override
    public boolean isMaterialEnabled(String material){
        return this.integrationConfigHelper.materialEnabled(material);
    }

    @Override
    public boolean isMaterialFluidEnabled(String material){
        return this.integrationConfigHelper.fluidEnabled(material);
    }

    @Override
    public void enableForceJsonCreation(){
        this.forceCreateJson = true;
    }

    // Helpers
    private MaterialConfigOptions getProperties(IBasicMaterial material) {
        return this.integrationConfigHelper.getSafeMaterialConfigOptions(material.getIdentifier());
    }

    private String returnAlloyExample() {
        return "//  {\n" +
                "//    \"output\": {\n" +
                "//      \"fluid\": \"iron\",\n" +
                "//      \"amount\": 144\n" +
                "//    },\n" +
                "//    \"inputs\": [\n" +
                "//      {\n" +
                "//        \"fluid\": \"copper\",\n" +
                "//        \"amount\": 108\n" +
                "//      },\n" +
                "//      {\n" +
                "//        \"fluid\": \"lead\",\n" +
                "//        \"amount\": 36\n" +
                "//      }\n" +
                "//    ]\n" +
                "//  }\n";
    }
}
