package davatar.wu.allowconcreteondirt;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import com.wurmonline.mesh.CaveTile;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.GeneralUtilities;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.NoSuchActionException;
import com.wurmonline.server.behaviours.Terraforming;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.Skills;
import com.wurmonline.server.structures.Fence;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;

import javassist.CtClass;
import javassist.CtPrimitiveType;
import javassist.bytecode.Descriptor;

public class AllowConcreteOnDirt implements WurmServerMod, PreInitable {
    private static Logger logger = Logger.getLogger("ConcreteOnDirt");
    
    public static void logException(String msg, Throwable e) {
        if (logger != null) { logger.log(Level.SEVERE, msg, e); }
    }

    public static void logInfo(String msg) {
        if (logger != null) { logger.log(Level.INFO, msg); }
    }
    public static void logInfo(String msg, Exception ex) {
        if (logger != null) { logger.log(Level.INFO, msg, ex); }
    }

    public String getVersion() {
    	return "0.1";
    }
    
	@Override
	public void preInit() {
		try { 
			CtClass[] paramTypes = {
					HookManager.getInstance().getClassPool().getCtClass("com.wurmonline.server.creatures.Creature"),
					HookManager.getInstance().getClassPool().getCtClass("com.wurmonline.server.items.Item"),
					CtPrimitiveType.intType,
					CtPrimitiveType.intType,
					CtPrimitiveType.floatType,
					HookManager.getInstance().getClassPool().getCtClass("com.wurmonline.server.behaviours.Action"),
				};
			logInfo("Concrete can now raise corners of dirt tiles and packed dirt tiles.");
			HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.CaveTileBehaviour", "raiseRockLevel", Descriptor.ofMethod(CtPrimitiveType.booleanType, paramTypes), new InvocationHandlerFactory() {
				@Override
				public InvocationHandler createInvocationHandler() {
					return new InvocationHandler() {
						@Override
						public Object invoke(Object object, Method method, Object[] args) throws Throwable {
							return raiseRockLevel((Creature)args[0], (Item)args[1], (int)args[2], (int)args[3], (float)args[4], (Action)args[5]);
						}
					};
				}
			});
		} catch(Exception e) {
			logException("preInit: ", e);
			e.printStackTrace();
			throw new HookException(e);
		}
	}

	public static final boolean raiseRockLevel(final Creature performer, final Item source, final int tilex, final int tiley, final float counter, final Action act) {
        if (!GeneralUtilities.isValidTileLocation(tilex, tiley)) {
            performer.getCommunicator().sendNormalServerMessage("The ground can not be raised here.");
            return true;
        }
        int tile = 0;
        if (performer.isOnSurface()) {
            tile = Server.surfaceMesh.getTile(tilex, tiley);
        }
        else {
            tile = Server.caveMesh.getTile(tilex, tiley);
            if (!Tiles.isReinforcedFloor(Tiles.decodeType(tile)) && anyReinforcedFloors(performer)) {
                performer.getCommunicator().sendNormalServerMessage("You cannot raise the corner next to reinforced floors.");
                return true;
            }
        }
        if (counter == 1.0f || counter == 0.0f || act.justTickedSecond()) {
            if (performer.getCurrentTile().getStructure() != null) {
                performer.getCommunicator().sendNormalServerMessage("This cannot be done in buildings.");
                return true;
            }
            if (Zones.protectedTiles[tilex][tiley]) {
                performer.getCommunicator().sendNormalServerMessage("For some strange reason you can't bring yourself to change this place.");
                return true;
            }
            if (Terraforming.isAltarBlocking(performer, tilex, tiley)) {
                performer.getCommunicator().sendNormalServerMessage("You cannot build here, since this is holy ground.");
                return true;
            }
            if (performer.getLayer() < 0) {
                if (CaveTile.decodeCeilingHeight(tile) <= 20) {
                    performer.getCommunicator().sendNormalServerMessage("The ceiling is too close.");
                    return true;
                }
                if (performer.getFloorLevel() > 0) {
                    performer.getCommunicator().sendNormalServerMessage("You must be standing on the ground in order to do this!");
                    return true;
                }
                if (Zones.isTileCornerProtected(tilex, tiley)) {
                    performer.getCommunicator().sendNormalServerMessage("This tile is protected by the gods. You can not raise the corner here.");
                    return true;
                }
            }
            else {
                if (performer.getFloorLevel() != 0) {
                    performer.getCommunicator().sendNormalServerMessage("You must be standing on the ground in order to do this!");
                    return true;
                }
                for (int x = 0; x >= -1; --x) {
                    for (int y = 0; y >= -1; --y) {
                        final int tx = Zones.safeTileX(tilex + x);
                        final int ty = Zones.safeTileY(tiley + y);
                        if (Tiles.decodeType(Server.caveMesh.getTile(tx, ty)) == Tiles.Tile.TILE_CAVE_EXIT.id) {
                            performer.getCommunicator().sendNormalServerMessage("The opening is too close.");
                            return true;
                        }
                        final int ttile = Server.surfaceMesh.getTile(tx, ty);
                        byte tileType = Tiles.decodeType(ttile);
                        if (tileType != Tiles.Tile.TILE_ROCK.id && tileType != Tiles.Tile.TILE_DIRT.id && tileType != Tiles.Tile.TILE_DIRT_PACKED.id) {
                            performer.getCommunicator().sendNormalServerMessage("Concrete can only raise rock and dirt tiles.");
                            return true;
                        }
                        final VolaTile vtile = Zones.getTileOrNull(tx, ty, performer.isOnSurface());
                        if (vtile != null) {
                            if (vtile.getStructure() != null) {
                                performer.getCommunicator().sendNormalServerMessage("The structure is in the way.");
                                return true;
                            }
                            if (x == 0 && y == 0) {
                                final Fence[] fences = vtile.getFences();
                                final int length = fences.length;
                                final int n = 0;
                                if (n < length) {
                                    final Fence fence = fences[n];
                                    performer.getCommunicator().sendNormalServerMessage("The " + fence.getName() + " is in the way.");
                                    return true;
                                }
                            }
                            else if (x == -1 && y == 0) {
                                for (final Fence fence : vtile.getFences()) {
                                    if (fence.isHorizontal()) {
                                        performer.getCommunicator().sendNormalServerMessage("The " + fence.getName() + " is in the way.");
                                        return true;
                                    }
                                }
                            }
                            else if (y == -1 && x == 0) {
                                for (final Fence fence : vtile.getFences()) {
                                    if (!fence.isHorizontal()) {
                                        performer.getCommunicator().sendNormalServerMessage("The " + fence.getName() + " is in the way.");
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
                final int slopeDown = Terraforming.getMaxSurfaceDownSlope(tilex, tiley);
                final int maxSlope = Servers.localServer.PVPSERVER ? -25 : -40;
                if (performer.getPower() > 4 && source.getTemplateId() == 176) {
                    if (slopeDown < -300) {
                        performer.getCommunicator().sendNormalServerMessage("Maximum slope would be exceeded.");
                        return true;
                    }
                }
                else if (slopeDown < maxSlope) {
                    if (performer.getPower() == 4 && source.getTemplateId() == 176) {
                        performer.getCommunicator().sendNormalServerMessage("Maximum slope would be exceeded.");
                    }
                    else {
                        performer.getCommunicator().sendNormalServerMessage("The " + source.getName() + " would only flow away.");
                    }
                    return true;
                }
            }
            if (source.getTemplateId() != 176 && source.getWeightGrams() < source.getTemplate().getWeightGrams()) {
                performer.getCommunicator().sendNormalServerMessage("The " + source.getName() + " contains too little material to be usable.");
                return true;
            }
        }
        boolean done = true;
        final short h = Tiles.decodeHeight(tile);
        if (h >= -25 || source.getTemplateId() == 176) {
            final Skills skills = performer.getSkills();
            Skill masonry = null;
            done = false;
            try {
                masonry = skills.getSkill(1013);
            }
            catch (Exception ex) {
                masonry = skills.learn(1013, 1.0f);
            }
            int time = 0;
            if (counter == 1.0f) {
                time = (int)Math.max(30.0, 100.0 - masonry.getKnowledge(source, 0.0));
                try {
                    performer.getCurrentAction().setTimeLeft(time);
                }
                catch (NoSuchActionException nsa) {
                	logInfo("This action does not exist?", nsa);
                }
                if (source.getTemplateId() == 176) {
                    performer.getCommunicator().sendNormalServerMessage("You will the rock to raise up.");
                }
                else {
                    performer.getCommunicator().sendNormalServerMessage("You start to spread out the " + source.getName() + ".");
                    Server.getInstance().broadCastAction(performer.getName() + " starts spreading the " + source.getName() + ".", performer, 5);
                }
                performer.sendActionControl(Actions.actionEntrys[518].getVerbString(), true, time);
            }
            else {
                try {
                    time = performer.getCurrentAction().getTimeLeft();
                }
                catch (NoSuchActionException nsa) {
                    logInfo("This action does not exist?", nsa);
                }
            }
            if (counter * 10.0f > time || source.getTemplateId() == 176) {
                if (source.getTemplateId() != 176) {
                    performer.getStatus().modifyStamina(-3000.0f);
                    source.setWeight(source.getWeightGrams() - source.getTemplate().getWeightGrams(), true);
                    masonry.skillCheck(1.0, source, 0.0, false, counter);
                    source.setDamage(source.getDamage() + 5.0E-4f * source.getDamageModifier());
                }
                done = true;
                if (performer.getLayer() < 0) {
                    Server.caveMesh.setTile(tilex, tiley, Tiles.encode((short)(Tiles.decodeHeight(tile) + 1), Tiles.decodeType(tile), (byte)(CaveTile.decodeCeilingHeight(tile) - 1)));
                    tile = Server.caveMesh.getTile(tilex, tiley);
                }
                else {
                    tile = Server.rockMesh.getTile(tilex, tiley);
                    Server.rockMesh.setTile(tilex, tiley, Tiles.encode((short)(Tiles.decodeHeight(tile) + 1), Tiles.decodeType(tile), Tiles.decodeData(tile)));
                    tile = Server.surfaceMesh.getTile(tilex, tiley);
                    Server.surfaceMesh.setTile(tilex, tiley, Tiles.encode((short)(Tiles.decodeHeight(tile) + 1), Tiles.decodeType(tile), Tiles.decodeData(tile)));
                }
                if (source.getTemplateId() != 176 && source.getWeightGrams() < source.getTemplate().getWeightGrams()) {
                    performer.getCommunicator().sendNormalServerMessage("The " + source.getName() + " contains too little material to be usable.");
                    return true;
                }
                Players.getInstance().sendChangedTile(tilex, tiley, performer.getLayer() >= 0, false);
                performer.getCommunicator().sendNormalServerMessage("You raise the ground a bit.");
                if (source.getTemplateId() != 176) {
                    Server.getInstance().broadCastAction(performer.getName() + " raises the ground a bit.", performer, 5);
                }
            }
        }
        else {
            performer.getCommunicator().sendNormalServerMessage("The water is too deep and would only dissolve the " + source.getName() + ".");
        }
        return done;
    }

    private static boolean anyReinforcedFloors(final Creature performer) {
        final int digTilex = (int)(performer.getStatus().getPositionX() + 2.0f) >> 2;
        final int digTiley = (int)(performer.getStatus().getPositionY() + 2.0f) >> 2;
        final int digTile = Server.caveMesh.getTile(digTilex, digTiley);
        final byte digType = Tiles.decodeType(digTile);
        return Tiles.isReinforcedFloor(digType) || Tiles.isRoadType(digType) || anyAdjacentReinforcedFloors(digTilex, digTiley, false);
    }
    
    private static boolean anyAdjacentReinforcedFloors(final int digTilex, final int digTiley, final boolean all) {
        int digTile = Server.caveMesh.getTile(digTilex - 1, digTiley - 1);
        byte digType = Tiles.decodeType(digTile);
        if (Tiles.isReinforcedFloor(digType) || Tiles.isRoadType(digType)) {
            return true;
        }
        digTile = Server.caveMesh.getTile(digTilex - 1, digTiley);
        digType = Tiles.decodeType(digTile);
        if (Tiles.isReinforcedFloor(digType) || Tiles.isRoadType(digType)) {
            return true;
        }
        digTile = Server.caveMesh.getTile(digTilex, digTiley - 1);
        digType = Tiles.decodeType(digTile);
        if (Tiles.isReinforcedFloor(digType) || Tiles.isRoadType(digType)) {
            return true;
        }
        if (all) {
            digTile = Server.caveMesh.getTile(digTilex + 1, digTiley - 1);
            digType = Tiles.decodeType(digTile);
            if (Tiles.isReinforcedFloor(digType) || Tiles.isRoadType(digType)) {
                return true;
            }
            digTile = Server.caveMesh.getTile(digTilex + 1, digTiley);
            digType = Tiles.decodeType(digTile);
            if (Tiles.isReinforcedFloor(digType) || Tiles.isRoadType(digType)) {
                return true;
            }
            digTile = Server.caveMesh.getTile(digTilex + 1, digTiley + 1);
            digType = Tiles.decodeType(digTile);
            if (Tiles.isReinforcedFloor(digType) || Tiles.isRoadType(digType)) {
                return true;
            }
            digTile = Server.caveMesh.getTile(digTilex, digTiley + 1);
            digType = Tiles.decodeType(digTile);
            if (Tiles.isReinforcedFloor(digType) || Tiles.isRoadType(digType)) {
                return true;
            }
            digTile = Server.caveMesh.getTile(digTilex - 1, digTiley + 1);
            digType = Tiles.decodeType(digTile);
            if (Tiles.isReinforcedFloor(digType) || Tiles.isRoadType(digType)) {
                return true;
            }
        }
        return false;
    }
}
