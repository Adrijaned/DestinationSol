/*
 * Copyright 2017 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.destinationsol.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import org.destinationsol.CommonDrawer;
import org.destinationsol.Const;
import org.destinationsol.GameOptions;
import org.destinationsol.SolApplication;
import org.destinationsol.common.DebugCol;
import org.destinationsol.common.SolMath;
import org.destinationsol.common.SolRandom;
import org.destinationsol.files.HullConfigManager;
import org.destinationsol.game.asteroid.AsteroidBuilder;
import org.destinationsol.game.chunk.ChunkManager;
import org.destinationsol.game.drawables.DrawableDebugger;
import org.destinationsol.game.drawables.DrawableManager;
import org.destinationsol.game.farBg.FarBackgroundManagerOld;
import org.destinationsol.game.input.AiPilot;
import org.destinationsol.game.input.BeaconDestProvider;
import org.destinationsol.game.input.Pilot;
import org.destinationsol.game.input.UiControlledPilot;
import org.destinationsol.game.item.Gun;
import org.destinationsol.game.item.ItemContainer;
import org.destinationsol.game.item.ItemManager;
import org.destinationsol.game.item.LootBuilder;
import org.destinationsol.game.item.MercItem;
import org.destinationsol.game.item.SolItem;
import org.destinationsol.game.item.TradeConfig;
import org.destinationsol.game.particle.EffectTypes;
import org.destinationsol.game.particle.PartMan;
import org.destinationsol.game.particle.SpecialEffects;
import org.destinationsol.game.planet.Planet;
import org.destinationsol.game.planet.PlanetManager;
import org.destinationsol.game.planet.SolSystem;
import org.destinationsol.game.planet.SunSingleton;
import org.destinationsol.game.screens.GameScreens;
import org.destinationsol.game.ship.ShipAbility;
import org.destinationsol.game.ship.ShipBuilder;
import org.destinationsol.game.ship.SloMo;
import org.destinationsol.game.ship.hulls.HullConfig;
import org.destinationsol.game.sound.OggSoundManager;
import org.destinationsol.game.sound.SpecialSounds;
import org.destinationsol.mercenary.MercenaryUtils;
import org.destinationsol.ui.DebugCollector;
import org.destinationsol.ui.TutorialManager;
import org.destinationsol.ui.UiDrawer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class SolGame {

    private static final String MERC_SAVE_FILE = "mercenaries.json";
    private static Logger logger = LoggerFactory.getLogger(SolGame.class);


    private final GameScreens gameScreens;
    private final SolCam camera;
    private final ObjectManager objectManager;
    private final SolApplication solApplication;
    private final DrawableManager drawableManager;
    private final PlanetManager planetManager;
    private final ChunkManager chunkManager;
    private final PartMan partMan;
    private final AsteroidBuilder asteroidBuilder;
    private final LootBuilder lootBuilder;
    private final ShipBuilder shipBuilder;
    private final HullConfigManager hullConfigManager;
    private final GridDrawer gridDrawer;
    private final FarBackgroundManagerOld farBackgroundManagerOld;
    private final FactionManager factionManager;
    private final MapDrawer mapDrawer;
    private final ShardBuilder shardBuilder;
    private final ItemManager itemManager;
    private final StarPort.Builder starPortBuilder;
    private final OggSoundManager soundManager;
    private final DrawableDebugger drawableDebugger;
    private final SpecialSounds specialSounds;
    private final SpecialEffects specialEffects;
    private final GameColors gameColors;
    private final BeaconHandler beaconHandler;
    private final MountDetectDrawer mountDetectDrawer;
    private final TutorialManager tutorialManager;
    private final GalaxyFiller galaxyFiller;
    private Hero hero;
    private String shipName; // Not updated in-game. Can be changed using setter
    private float timeStep;
    private float time;
    private boolean paused;
    private float timeFactor;
    private float respawnMoney;
    private RespawnState respawnState;

    public SolGame(SolApplication cmp, String shipName, boolean tut, boolean isNewGame, CommonDrawer commonDrawer) {
        solApplication = cmp;
        GameDrawer drawer = new GameDrawer(commonDrawer);
        gameColors = new GameColors();
        soundManager = solApplication.getSoundManager();
        specialSounds = new SpecialSounds(soundManager);
        drawableManager = new DrawableManager(drawer);
        camera = new SolCam(drawer.r);
        gameScreens = new GameScreens(drawer.r, cmp);
        tutorialManager = tut ? new TutorialManager(commonDrawer.dimensionsRatio, gameScreens, cmp.isMobile(), cmp.getOptions(), this) : null;
        farBackgroundManagerOld = new FarBackgroundManagerOld();
        shipBuilder = new ShipBuilder();
        EffectTypes effectTypes = new EffectTypes();
        specialEffects = new SpecialEffects(effectTypes, gameColors);
        itemManager = new ItemManager(soundManager, effectTypes, gameColors);
        AbilityCommonConfigs abilityCommonConfigs = new AbilityCommonConfigs(effectTypes, gameColors, soundManager);
        hullConfigManager = new HullConfigManager(itemManager, abilityCommonConfigs);
        SolNames solNames = new SolNames();
        planetManager = new PlanetManager(hullConfigManager, gameColors, itemManager);
        SolContactListener contactListener = new SolContactListener(this);
        factionManager = new FactionManager();
        objectManager = new ObjectManager(contactListener, factionManager);
        gridDrawer = new GridDrawer();
        chunkManager = new ChunkManager();
        partMan = new PartMan();
        asteroidBuilder = new AsteroidBuilder();
        lootBuilder = new LootBuilder();
        mapDrawer = new MapDrawer(commonDrawer.height);
        shardBuilder = new ShardBuilder();
        galaxyFiller = new GalaxyFiller();
        starPortBuilder = new StarPort.Builder();
        drawableDebugger = new DrawableDebugger();
        beaconHandler = new BeaconHandler();
        mountDetectDrawer = new MountDetectDrawer();
        timeFactor = 1;

        // from this point we're ready!
        planetManager.fill(solNames);
        respawnState = new RespawnState();
        createGame(shipName, isNewGame, this);
        if (!isNewGame) {
            createAndSpawnMercenariesFromSave();
        }
        SolMath.checkVectorsTaken(null);
    }

    private void createGame(String shipName, boolean isNewGame, SolGame game) {
        ShipConfig shipConfig = readShipFromConfigOrLoadFromSaveIfNull(shipName, game);
        hero = createPlayer(shipConfig,
                isNewGame,
                respawnState,
                this,
                solApplication.getOptions().controlType == GameOptions.CONTROL_MOUSE,
                respawnMoney);
    }

    private ShipConfig readShipFromConfigOrLoadFromSaveIfNull(String shipName, SolGame game) {
        if (shipName != null) {
            return ShipConfig.load(game.getHullConfigs(), shipName, game.getItemMan(), game);
        } else {
            return SaveManager.readShip(game.getHullConfigs(), game.getItemMan(), game);
        }
    }

    // uh, this needs refactoring
    private Hero createPlayer(ShipConfig shipConfig, boolean isNewGame, RespawnState respawnState, SolGame game, boolean isMouseControl, float respawnMoney) {

        // Added temporarily to remove warnings. Handle this more gracefully inside the SaveManager.readShip and the ShipConfig.load methods
        assert shipConfig != null;

        if (!respawnState.isPlayerRespawned()) {
            game.getGalaxyFiller().fill(game, game.getHullConfigs(), game.getItemMan());
        }

        // If we continue a game, we should spawn from the same position
        Vector2 position;
        if (isNewGame) {
            position = game.getGalaxyFiller().getPlayerSpawnPos(game);
        } else {
            position = shipConfig.spawnPos;
        }
        game.getCam().setPos(position);

        Pilot pilot;
        if (isMouseControl) {
            game.getBeaconHandler().init(game, position);
            pilot = new AiPilot(new BeaconDestProvider(), true, Faction.LAANI, false, "you", Const.AI_DET_DIST);
        } else {
            pilot = new UiControlledPilot(game.getScreens().mainScreen);
        }

        float money = respawnMoney != 0 ? respawnMoney : game.getTutMan() != null ? 200 : shipConfig.money;

        HullConfig hull = respawnState.getRespawnHull() != null ? respawnState.getRespawnHull() : shipConfig.hull;

        String itemsStr = !respawnState.getRespawnItems().isEmpty() ? "" : shipConfig.items;

        boolean giveAmmo = shipName != null && respawnState.getRespawnItems().isEmpty();
        Hero hero = new Hero(game.getShipBuilder().buildNewFar(game, new Vector2(position), null, 0, 0, pilot, itemsStr, hull, null, true, money, new TradeConfig(), giveAmmo).toObject(game));
        ItemContainer itemContainer = hero.getItemContainer();
        if (!respawnState.getRespawnItems().isEmpty()) {
            for (SolItem item : respawnState.getRespawnItems()) {
                itemContainer.add(item);
                // Ensure that previously equipped items stay equipped
                if (item.isEquipped() > 0) {
                    if (item instanceof Gun) {
                        hero.maybeEquip(game, item, item.isEquipped() == 2, true);
                    } else {
                        hero.maybeEquip(game, item, true);
                    }
                }
            }
        } else if (game.getTutMan() != null) {
            for (int i = 0; i < 50; i++) {
                if (itemContainer.groupCount() > 1.5f * Const.ITEM_GROUPS_PER_PAGE) {
                    break;
                }
                SolItem it = game.getItemMan().random();
                if (!(it instanceof Gun) && it.getIcon(game) != null && itemContainer.canAdd(it)) {
                    itemContainer.add(it.copy());
                }
            }
        }
        itemContainer.markAllAsSeen();

        // Don't change equipped items across load/respawn
        //AiPilot.reEquip(this, myHero);

        game.getObjectManager().addObjDelayed(hero.getShip());
        game.getObjectManager().resetDelays();
        return hero;
    }

    private void createAndSpawnMercenariesFromSave() {
        List<MercItem> mercenaryItems = new MercenarySaveLoader()
                .loadMercenariesFromSave(hullConfigManager, itemManager, MERC_SAVE_FILE);
        for (MercItem mercenaryItem : mercenaryItems) {
            MercenaryUtils.createMerc(this, hero, mercenaryItem);
        }
    }

    public void onGameEnd() {
        saveShip();
        saveWorld();
        objectManager.dispose();
    }

    /**
     * Saves the world's seed so we can regenerate the same world later
     */
    private void saveWorld() {
        // Make sure the tutorial doesn't overwrite the save
        if (tutorialManager == null) {
            long seed = SolRandom.getSeed();

            String fileName = SaveManager.getResourcePath(SolApplication.WORLD_SAVE_FILE_NAME);

            String toWrite = "seed=" + Long.toString(seed);

            PrintWriter writer;
            try {
                writer = new PrintWriter(fileName, "UTF-8");
                writer.write(toWrite);
                writer.close();
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                logger.error("Could not save galaxy seed, " + e.getMessage());
                return;
            }
            logger.info("Successfully saved the galaxy seed: " + String.valueOf(seed));
        }
    }

    private void saveShip() {
        if (tutorialManager != null) {
            return;
        }

        HullConfig hull;
        float money;
        List<SolItem> items;

        if (hero.isAlive()) {
            hull = hero.isTranscendent() ? hero.getTranscendentHero().getShip().getHullConfig() : hero.getHull().config;
            money = hero.getMoney();
            items = new ArrayList<>();
            for (List<SolItem> group : hero.getItemContainer()) {
                for (SolItem i : group) {
                    items.add(0, i);
                }
            }
        } else {
            hull = respawnState.getRespawnHull();
            money = respawnMoney;
            items = respawnState.getRespawnItems();
        }

        SaveManager.writeShips(hull, money, items, this);
    }

    public GameScreens getScreens() {
        return gameScreens;
    }

    public void update() {
        drawableDebugger.update(this);

        if (paused) {
            camera.updateMap(this); // update zoom only for map
            mapDrawer.update(this); // animate map icons
            return;
        }

        timeFactor = DebugOptions.GAME_SPEED_MULTIPLIER;
        if (hero.isAlive() && hero.isNonTranscendent()) {
            ShipAbility ability = hero.getAbility();
            if (ability instanceof SloMo) {
                float factor = ((SloMo) ability).getFactor();
                timeFactor *= factor;
            }
        }
        timeStep = Const.REAL_TIME_STEP * timeFactor;
        time += timeStep;

        planetManager.update(this);
        camera.update(this);
        chunkManager.update(this);
        mountDetectDrawer.update(this);
        objectManager.update(this);
        mapDrawer.update(this);
        soundManager.update(this);
        beaconHandler.update(this);

        if (tutorialManager != null) {
            tutorialManager.update();
        }
    }

    public void draw() {
        drawableManager.draw(this);
    }

    public void drawDebug(GameDrawer drawer) {
        if (DebugOptions.GRID_SZ > 0) {
            gridDrawer.draw(drawer, this, DebugOptions.GRID_SZ, drawer.debugWhiteTexture);
        }
        planetManager.drawDebug(drawer, this);
        objectManager.drawDebug(drawer, this);
        if (DebugOptions.ZOOM_OVERRIDE != 0) {
            camera.drawDebug(drawer);
        }
        drawDebugPoint(drawer, DebugOptions.DEBUG_POINT, DebugCol.POINT);
        drawDebugPoint(drawer, DebugOptions.DEBUG_POINT2, DebugCol.POINT2);
        drawDebugPoint(drawer, DebugOptions.DEBUG_POINT3, DebugCol.POINT3);
    }

    private void drawDebugPoint(GameDrawer drawer, Vector2 dp, Color col) {
        if (dp.x != 0 || dp.y != 0) {
            float sz = camera.getRealLineWidth() * 5;
            drawer.draw(drawer.debugWhiteTexture, sz, sz, sz / 2, sz / 2, dp.x, dp.y, 0, col);
        }
    }

    public float getTimeStep() {
        return timeStep;
    }

    public SolCam getCam() {
        return camera;
    }

    public SolApplication getCmp() {
        return solApplication;
    }

    public DrawableManager getDrawableManager() {
        return drawableManager;
    }

    public ObjectManager getObjectManager() {
        return objectManager;
    }

    public PlanetManager getPlanetManager() {
        return planetManager;
    }

    public PartMan getPartMan() {
        return partMan;
    }

    public AsteroidBuilder getAsteroidBuilder() {
        return asteroidBuilder;
    }

    public LootBuilder getLootBuilder() {
        return lootBuilder;
    }

    public Hero getHero() {
        return hero;
    }

    public ShipBuilder getShipBuilder() {
        return shipBuilder;
    }

    public ItemManager getItemMan() {
        return itemManager;
    }

    public HullConfigManager getHullConfigs() {
        return hullConfigManager;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        DebugCollector.warn(this.paused ? "game paused" : "game resumed");
    }

    public void respawn() {
        if (hero.isAlive()) {
            if (hero.isNonTranscendent()) {
                beforeHeroDeath();
                objectManager.removeObjDelayed(hero.getShip());
            } else {
                setRespawnState(hero.getMoney(), hero.getItemContainer(), hero.getTranscendentHero().getShip().getHullConfig());
                objectManager.removeObjDelayed(hero.getTranscendentHero());
            }
        }
        // TODO: Consider whether we want to treat respawn as a newGame or not.
        createGame(null, true, this);
    }

    public FactionManager getFactionMan() {
        return factionManager;
    }

    public boolean isPlaceEmpty(Vector2 position, boolean considerPlanets) {
        if (considerPlanets) {
            Planet np = planetManager.getNearestPlanet(position);
            boolean inPlanet = np.getPosition().dst(position) < np.getFullHeight();

            if (inPlanet) {
                return false;
            }
        }

        SolSystem ns = planetManager.getNearestSystem(position);
        if (ns.getPosition().dst(position) < SunSingleton.SUN_HOT_RAD) {
            return false;
        }

        List<SolObject> objs = objectManager.getObjects();
        for (SolObject o : objs) {
            if (!o.hasBody()) {
                continue;
            }

            if (position.dst(o.getPosition()) < objectManager.getRadius(o)) {
                return false;
            }
        }

        for (FarObjData fod : objectManager.getFarObjs()) {
            FarObject o = fod.fo;

            if (!o.hasBody()) {
                continue;
            }

            if (position.dst(o.getPosition()) < o.getRadius()) {
                return false;
            }
        }

        return true;
    }

    public MapDrawer getMapDrawer() {
        return mapDrawer;
    }

    public ShardBuilder getShardBuilder() {
        return shardBuilder;
    }

    public FarBackgroundManagerOld getFarBackgroundgManagerOld() {
        return farBackgroundManagerOld;
    }

    public GalaxyFiller getGalaxyFiller() {
        return galaxyFiller;
    }

    public StarPort.Builder getStarPortBuilder() {
        return starPortBuilder;
    }

    public GridDrawer getGridDrawer() {
        return gridDrawer;
    }

    public OggSoundManager getSoundManager() {
        return soundManager;
    }

    public float getTime() {
        return time;
    }

    public void drawDebugUi(UiDrawer uiDrawer) {
        drawableDebugger.draw(uiDrawer);
    }

    public SpecialSounds getSpecialSounds() {
        return specialSounds;
    }

    public SpecialEffects getSpecialEffects() {
        return specialEffects;
    }

    public GameColors getCols() {
        return gameColors;
    }

    public float getTimeFactor() {
        return timeFactor;
    }

    public BeaconHandler getBeaconHandler() {
        return beaconHandler;
    }

    public MountDetectDrawer getMountDetectDrawer() {
        return mountDetectDrawer;
    }

    public TutorialManager getTutMan() {
        return tutorialManager;
    }

    public String getShipName() {
        return shipName;
    }

    public void setShipName(String newName) {
        shipName = newName;
    }

    public void beforeHeroDeath() {
        if (hero.isDead() || hero.isTranscendent()) {
            return;
        }

        float money = hero.getMoney();
        ItemContainer itemContainer = hero.getItemContainer();

        setRespawnState(money, itemContainer, hero.getHull().config);

        hero.setMoney(money - respawnMoney);
        for (SolItem item : respawnState.getRespawnItems()) {
            itemContainer.remove(item);
        }
    }

    private void setRespawnState(float money, ItemContainer ic, HullConfig hullConfig) {
        respawnMoney = .75f * money;
        respawnState.setRespawnHull(hullConfig);
        respawnState.getRespawnItems().clear();
        respawnState.setPlayerRespawned(true);
        for (List<SolItem> group : ic) {
            for (SolItem item : group) {
                boolean equipped = hero.isTranscendent() || hero.maybeUnequip(this, item, false);
                if (equipped || SolRandom.test(.75f)) {
                    respawnState.getRespawnItems().add(0, item);
                }
            }
        }
    }
}
