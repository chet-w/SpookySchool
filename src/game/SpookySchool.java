package game;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;


/**
 * This class contains all of the logic Spooky School game. This class controls game state and provides various helper methods
 * for the server.
 * @author Pritesh R. Patel
 *
 */
public class SpookySchool {

	private final Position defaultSpawnPosition = new Position(5, 8); //Default position that a player spawns in, in a spawn room.

	private Map<String, Area> areas = new HashMap<String, Area>();
	private List<Player> players = new ArrayList<Player>();
	private List<String> chatLog = new ArrayList<String>();

	private Map<String, Bundle> playerBundles = new HashMap<String, Bundle>();

	public SpookySchool() {
		this.loadAreas(); //Load maps
		this.setDoors(); //Sets up doors on the areas.

		System.out.println("Game Loaded.");
	}

	/**
	 * Load all of the "areas" of the game into the list of areas.
	 */
	public void loadAreas() {
		Scanner scan;
		try {
			scan = new Scanner(new File("src/areas/areas.txt"));
			while (scan.hasNextLine()) {
				String areaName = scan.next();
				String fileName = scan.next();
				this.areas.put(areaName, new Area(areaName, fileName));
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}


	/**
	 * Sets up the doors in the areas.
	 */
	public void setDoors() {
		Scanner scan;

		try {
			scan = new Scanner(new File("src/areas/doors.txt"));
			while (scan.hasNextLine()) {

				//Scan Door Specific information
				String doorID = scan.next();
				String token = scan.next();
				boolean open = scan.next().equals("open");
				boolean locked = scan.next().equals("locked");
				String keyID = scan.next();
				keyID = keyID.equals("null") ? null : keyID;

				//Scan information about rooms on either side.
				String sideA = scan.next();
				Position sideADoorPos = new Position(scan.nextInt(), scan.nextInt());
				Position sideAEntryPos = new Position(scan.nextInt(), scan.nextInt());

				String sideB = scan.next();
				Position sideBDoorPos = new Position(scan.nextInt(), scan.nextInt());
				Position sideBEntryPos = new Position(scan.nextInt(), scan.nextInt());

				//Create the door object.
				DoorGO door = new DoorGO(doorID, token, open, locked, keyID, sideA, sideADoorPos, sideAEntryPos, sideB,
						sideBDoorPos, sideBEntryPos);


				//Get the area objects of both sides.
				Area areaA = this.areas.get(sideA);
				Area areaB = this.areas.get(sideB);

				//Add the door objects onto the appropriate tiles in their area...
				areaA.getTile(sideADoorPos).setOccupant(door);
				areaB.getTile(sideBDoorPos).setOccupant(door);

			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}


	/**
	 * Adds player to the game if the game is not full and player with this name does not already exist.
	 * @param name of the player being added to the game.
	 * @return true if player successfully added, otherwise false.
	 */
	public synchronized boolean addPlayer(String name) {

		if (this.players.size() < 8 && this.getPlayer(name) == null) {
			Area spawnRoom = this.findEmptySpawnRoom();
			Player newPlayer = new Player(name, spawnRoom, this.defaultSpawnPosition);
			spawnRoom.setOwner(newPlayer); //Set player as the owner of the spawn room.

			//Set the player as the occupant of the tile.
			FloorTile spawnTile = (FloorTile) spawnRoom.getTile(this.defaultSpawnPosition);

			assert spawnTile != null;

			spawnTile.setOccupant(newPlayer);
			this.players.add(newPlayer); //Add the player to the list of players in the game.
			this.chatLog.add(name + " entered the game.");

			//Set up the bundle for the new player.
			Bundle bundle = new Bundle(name);
			bundle.setPlayerObj(newPlayer);
			bundle.setNewArea(spawnRoom);
			bundle.setChatLog(this.chatLog);

			this.playerBundles.put(name, bundle);

			return true;
		}
		return false;
	}


	/**
	 * Remove player from the game
	 * @param name of the player to remove from the game
	 * FIXME Ensure everything relevant to the disconnecting player is removed!
	 */
	public synchronized void removePlayer(String name) {

		//Remove player as their spawn area's owner
		if (this.getPlayer(name).getCurrentArea().getAreaName().contains("Spawn")) {
			this.getPlayer(name).getCurrentArea().setOwner(null); //Set the players spawn room owner as null
		}

		this.getPlayer(name).getCurrentArea().getTile(this.getPlayer(name).getCurrentPosition()).removeOccupant(); //Remove player from the tile
		this.players.remove(this.getPlayer(name)); //Remove the player from this game by removing them from players list.
		this.playerBundles.remove(name); //Remove this player's bundle.
	}


	/**
	 * Finds and returns a spawn area that is currently not owned by a player.
	 * @return a spawn area that is currently not owned by a player.
	 */
	public Area findEmptySpawnRoom() {
		//Finds an unoccupied/un-owned spawn area and returns it.
		for (Entry<String, Area> entry : this.areas.entrySet()) {
			if (entry.getKey().contains("Spawn") && (!entry.getValue().hasOwner())) {
				return entry.getValue();
			}
		}

		throw new Error("Error: Could not find an empty spawn room. This should not be possible.");
	}

	/**
	 * Moves player in a given direction if possible.
	 * @param playerName the name of the player to move.
	 * @param direction the direction the player needs to move into.
	 * @return true if player moves to a new tile or changes direction.. Otherwise false.
	 */
	public synchronized boolean movePlayer(String playerName, String direction) {

		Player player = this.getPlayer(playerName);
		//Ensure that the player we are trying to move exists.
		if (player == null) {
			throw new Error("Player (" + playerName + ") you are trying to move does not exist in game.");
		}

		//If player is facing a different direction than the direction given, make the player face the given direction.
		if (!player.getDirection().equals(direction)) {
			this.getBundle(playerName).addGameObjectChange(playerName + " " + "direction " + direction.toString());
			player.setDirection(direction);
			return true;
		}

		//Player is facing the correct direction. attempt to move player in the given direction.
		switch (direction) {
		case "NORTH":
			return this.movePlayerNorth(player);
		case "SOUTH":
			return this.movePlayerSouth(player);
		case "EAST":
			return this.movePlayerEast(player);
		case "WEST":
			return this.movePlayerWest(player);
		}

		throw new Error("ERROR in movePlayer() method.");
	}

	/**
	 * Attempts to move the player NORTH by one tile.
	 * This method should be used once the player is facing the correct direction.
	 * @param player that is to be moved
	 * @return true if movement to the north is successful, otherwise false.
	 */
	private boolean movePlayerNorth(Player player) {
		int posX = player.getCurrentPosition().getPosX();
		int potentialPosY = player.getCurrentPosition().getPosY() - 1;

		Tile potentialTile = null;

		//Check if potential new position is within the bounds of the array.
		if (potentialPosY >= 0) {
			potentialTile = player.getCurrentArea().getTile(new Position(posX, potentialPosY));
		} else {
			return false; //Not a valid move.
		}

		//If the potential tile is a floor tile and is not currently occupied, then move the player.
		if (potentialTile instanceof FloorTile && (!((FloorTile) potentialTile).isOccupied())) {
			((FloorTile) player.getCurrentArea().getTile(player.getCurrentPosition())).removeOccupant(); //Remove player from old tile
			this.movePlayerToTile(player, potentialTile); //Move the player to the new tile.
			this.getBundle(player.getPlayerName()).addGameObjectChange(player.getPlayerName() + " " + "move NORTH");
			return true; //Player movement complete.
		}

		return processDoorMovement(potentialTile, player); //Return true if there is a door movement, else false.

	}

	/**
	 * Attempts to move the player SOUTH by one tile.
	 * This method should be called once the player is facing the correct direction.
	 * @param player that is to be moved
	 * @return true if successful, otherwise false.
	 */
	private boolean movePlayerSouth(Player player) {
		int posX = player.getCurrentPosition().getPosX();
		int potentialPosY = player.getCurrentPosition().getPosY() + 1;

		Tile potentialTile = null;

		//Check if potential new position is within the bounds of the array.
		if (potentialPosY < player.getCurrentArea().height) {
			potentialTile = player.getCurrentArea().getTile(new Position(posX, potentialPosY));
		} else {
			return false; //Not a valid move.
		}

		//If the potential tile is a floor tile and is not currently occupied, then move the player.
		if (potentialTile instanceof FloorTile && (!((FloorTile) potentialTile).isOccupied())) {
			((FloorTile) player.getCurrentArea().getTile(player.getCurrentPosition())).removeOccupant(); //Remove player from old tile
			this.movePlayerToTile(player, potentialTile); //Move the player to the new tile.
			this.getBundle(player.getPlayerName()).addGameObjectChange(player.getPlayerName() + " " + "move SOUTH");
			return true; //Player movement complete.
		}

		return processDoorMovement(potentialTile, player); //Return true if there is a door movement, else false.

	}

	/**
	 * Attempts to move the player EAST by one tile.
	 * This method should be used once the player is facing the correct direction.
	 * @param player that is to be moved
	 * @return true if movement to the east is successful, otherwise false.
	 */
	private boolean movePlayerEast(Player player) {
		int potentialPosX = player.getCurrentPosition().getPosX() + 1;
		int posY = player.getCurrentPosition().getPosY();

		Tile potentialTile = null;

		//Check if potential new position is within the bounds of the array.
		if (potentialPosX < player.getCurrentArea().width) {
			potentialTile = player.getCurrentArea().getTile(new Position(potentialPosX, posY));
		} else {
			return false; //Not a valid move.
		}

		//If the potential tile is a floor tile and is not currently occupied, then move the player.
		if (potentialTile instanceof FloorTile && (!((FloorTile) potentialTile).isOccupied())) {
			((FloorTile) player.getCurrentArea().getTile(player.getCurrentPosition())).removeOccupant(); //Remove player from old tile
			this.movePlayerToTile(player, potentialTile); //Move the player to the new tile.
			this.getBundle(player.getPlayerName()).addGameObjectChange(player.getPlayerName() + " " + "move EAST");
			return true; //Player movement complete.
		}

		return processDoorMovement(potentialTile, player); //Return true if there is a door movement, else false.
	}

	/**
	 * Attempts to move the player WEST by one tile.
	 * This method should be used once the player is facing the correct direction.
	 * @param player that is to be moved
	 * @return true if movement to the west is successful, otherwise false.
	 */
	private boolean movePlayerWest(Player player) {

		int potentialPosX = player.getCurrentPosition().getPosX() - 1;
		int posY = player.getCurrentPosition().getPosY();

		Tile potentialTile = null;

		//Check if potential new position is within the bounds of the array.
		if (potentialPosX >= 0) {
			potentialTile = player.getCurrentArea().getTile(new Position(potentialPosX, posY));
		} else {
			return false; //Not a valid Move
		}

		//If the potential tile is a floor tile and is not currently occupied, then move the player.
		if (potentialTile instanceof FloorTile && (!((FloorTile) potentialTile).isOccupied())) {
			((FloorTile) player.getCurrentArea().getTile(player.getCurrentPosition())).removeOccupant(); //Remove player from old tile
			this.movePlayerToTile(player, potentialTile); //Move the player to the new tile.
			this.getBundle(player.getPlayerName()).addGameObjectChange(player.getPlayerName() + " " + "move WEST");
			return true; //Player movement complete.
		}

		return processDoorMovement(potentialTile, player); //Return true if there is a door movement, else false.

	}


	/**
	 * Move player to next room if they move on to a door.
	 * @param potentialTile the tile that the player has tried to move on to.
	 * @param player the player that has tried to move.
	 * @return true if player moves to a new room successfully and false otherwise.
	 */
	public boolean processDoorMovement(Tile potentialTile, Player player) {

		//If the potential tile is a wall tile and has a door game object on it...
		//FIXME change this code to improve door functionality...
		if (potentialTile instanceof WallTile && potentialTile.getOccupant() instanceof DoorGO) {

			String playerName = player.getPlayerName();

			DoorGO door = (DoorGO) potentialTile.getOccupant();

			String currentSide = player.getCurrentArea().getAreaName();
			String otherSide = door.getOtherSide(currentSide);

			Area otherSideArea = this.areas.get(otherSide);

			Tile otherSideTile = otherSideArea.getTile(door.getOtherSideEntryPos(currentSide)); //The tile on the other side of the door.

			//If the door is open and the position on the other side is not occupied, then move player.
			if (door.isOpen() && !otherSideTile.isOccupied()) {

				player.getCurrentArea().getTile(player.getCurrentPosition()).removeOccupant(); //Remove player from this tile.

				player.setCurrentArea(this.areas.get(otherSide)); //Set the player's new area.

				this.getBundle(playerName).setNewArea(player.getCurrentArea()); //Add new area to the bundle. 

				this.movePlayerToTile(player, otherSideTile); //Add player to the new tile.

				this.getBundle(playerName).addGameObjectChange(playerName + " " + "newRoom null");

				this.getBundle(playerName)
						.addToLog(playerName + "entered the following room " + otherSide.replace('_', ' '));

				return true;
			}
		}

		return false;
	}

	/**
	 * Move the player to the given tile. Method moves player to new tile and removes them from the old tile. Also sets player's position to
	 * the new position.
	 * @param player to be moved.
	 * @param tile that the player needs to be moved onto.
	 */
	public void movePlayerToTile(Player player, Tile tile) {
		FloorTile newTile = (FloorTile) tile;
		newTile.setOccupant(player); //Add player to new tile.
		player.setCurrentPosition(newTile.getPosition()); //Set the player's new position.
	}


	/**
	 * Returns the player associated with the given player name.
	 * @param playerName name of the player to check for.
	 * @return the Player object of the given name if one exists, otherwise return null.
	 */
	public Player getPlayer(String playerName) {
		for (int i = 0; i < this.players.size(); i++) {
			if (this.players.get(i).getPlayerName().equals(playerName)) {
				return this.players.get(i);
			}
		}
		return null; //Player with given name not found.
	}

	/**
	 * Returns the bundle of the given player name.
	 * @param playerName of the player we are getting the bundle for.
	 * @return bundle of the playerName given.
	 */
	public synchronized Bundle getBundle(String playerName) {
		return this.playerBundles.get(playerName);
	}

}