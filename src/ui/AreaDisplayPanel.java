package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import game.Area;
import game.Bundle;
import game.DoorGO;
import game.FixedContainerGO;
import game.FloorTile;
import game.GameObject;
import game.MarkerGO;
import game.Player;
import game.Position;
import game.Tile;
import game.WallTile;
import network.Client;

/**
 * The window where the game is rendered
 *
 * @author cameronmclachlan
 *
 */
public class AreaDisplayPanel extends JPanel implements KeyListener, MouseListener {

	private OverlayPanel overlayPanel;

	// Window size and offset
	private final int windowOffSetX = 0;
	private final int windowOffSetY = 0;
	private final int windowWidth = 600; //352
	private final int windowHeight = 500;

	// Renderer Tile Size
	private final int tileWidth = 32;
	private final int tileHeight = 25;

	// x and Y buff when inbetween tiles
	private int mainPlayerXBuff; // 0 >= xBuff <= 32
	private int mainPlayerYBuff; // 0 >= xBuff <= 32

	// Map Offset
	private int renderOffSetX;
	private int renderOffSetY;

	//For rain
	private int nextRain = 0;
	private int delay = 5;

	// For access to DebugDisplay
	private GameFrame gameFrame;

	private Client client;
	private SpriteMap spriteMap;

	private Area currentArea;
	private Player mainPlayer;

	//For animation.
	private boolean animating = false;
	private boolean rightFoot = true;
	private List<GameObject> currentAreaObjects = new ArrayList<GameObject>();
	private List<GameObject> previousAreaObjects = new ArrayList<GameObject>();
	private List<AnimationObject> toAnimate = new ArrayList<AnimationObject>();

	// Current Rotational view 0-3
	private int view;
	/*			2
			 _______
			|		|
		3	|		|	1
			|_______|
	
				0
		  Default view */

	public AreaDisplayPanel(Client client, GameFrame gf, SpriteMap spriteMap) {

		// setup panel
		this.setBackground(Color.darkGray);
		this.setFocusable(true);
		this.requestFocus();
		this.addKeyListener(this);
		this.addMouseListener(this);
		this.spriteMap = new SpriteMap();
		this.overlayPanel = new OverlayPanel(this, spriteMap);

		this.spriteMap = spriteMap;
		this.setLayout(new BorderLayout());

		validate();
		this.client = client;
		this.gameFrame = gf;
	}

	/**
	 * Creates a
	 * @param overlayPanel
	 */
	public void setOverLay(OverlayPanel overlayPanel) {
		this.overlayPanel = overlayPanel;
		overlayPanel.setOpaque(false);
		this.add(overlayPanel, BorderLayout.CENTER);
	}

	/**
	 * Process the received bundle. Display game according to the bundle.
	 */
	public void processBundle(Bundle bundle) {

		//Set the footer message if there is one in the bundle.
		if (bundle.getMessage() != null) {
			overlayPanel.setFooterMessage(bundle.getMessage());
		}

		this.mainPlayer = bundle.getPlayerObj();

		this.previousAreaObjects = this.currentAreaObjects;

		if (currentArea == null) {
			this.currentArea = this.mainPlayer.getCurrentArea();
			this.currentAreaObjects = bundle.getAreaObjects();
			this.displayRoomName();
			this.centerPlayer();
			return;

		} else {

			String oldArea = currentArea.getAreaName();

			if (!oldArea.equals(this.mainPlayer.getCurrentArea().getAreaName())) {

				this.toAnimate.clear(); //New room so clear animation. for presentation day.

				this.currentArea = this.mainPlayer.getCurrentArea();
				this.currentAreaObjects = bundle.getAreaObjects();

				//Set the footer message if there is one in the bundle.
				if (bundle.getMessage() != null) {
					overlayPanel.setFooterMessage(bundle.getMessage());
				}

				this.displayRoomName();
				this.centerPlayer();
				return;
			}

			this.currentAreaObjects = bundle.getAreaObjects();
			this.currentArea = this.mainPlayer.getCurrentArea();
		}

		this.addChanges();
	}

	/**
	 * Find the changes that have occurred in the area since the last copy of the area was received, and add them to the toAnimate map.
	 */
	private void addChanges() {

		if (this.previousAreaObjects == null) {
			//Nothing to animate!
			return;
		}

		for (int i = 0; i < this.currentAreaObjects.size(); i++) {
			for (int j = 0; j < this.previousAreaObjects.size(); j++) {
				if (this.previousAreaObjects.get(j).getId().equals(this.currentAreaObjects.get(i).getId())) {

					GameObject previousObj = this.previousAreaObjects.get(j);
					GameObject currentObj = this.currentAreaObjects.get(i);

					//FIXME: Only allowing player animation for now.
					if (!(previousObj instanceof Player)) {
						continue;
					}

					//ASSUMING MOVEMENT IN ONLY ONE DIRECTION!!!
					if (currentObj.getPosition().getPosX() != previousObj.getPosition().getPosX()
							|| currentObj.getPosition().getPosY() != previousObj.getPosition().getPosY()) {

						//Starting position of the animation object.
						int startX = previousObj.getPosition().getPosX();
						int startY = previousObj.getPosition().getPosY();

						//Finishing position of the animation object.
						int aimX = currentObj.getPosition().getPosX();
						int aimY = currentObj.getPosition().getPosY();

						int[] view = this.getRotatedView(startX, startY, this.currentArea.width,
								this.currentArea.height);
						startX = view[0];
						startY = view[1];

						view = this.getRotatedView(aimX, aimY, this.currentArea.width, this.currentArea.height);
						aimX = view[0];
						aimY = view[1];

						boolean isMain = currentObj.getId().equals(this.mainPlayer.getId());

						AnimationObject aObj = null;

						if (currentObj.getPosition().getPosX() > previousObj.getPosition().getPosX()) {

							if (this.view == 0 || this.view == 2) {
								aObj = new AnimationObject(this, currentObj, isMain, this.determineDirection("EAST"),
										startX, startY, aimX, aimY);
							} else {
								aObj = new AnimationObject(this, currentObj, isMain, this.determineDirection("WEST"),
										startX, startY, aimX, aimY);
							}

						} else if (currentObj.getPosition().getPosX() < previousObj.getPosition().getPosX()) {

							if (this.view == 0 || this.view == 2) {
								aObj = new AnimationObject(this, currentObj, isMain, this.determineDirection("WEST"),
										startX, startY, aimX, aimY);
							} else {
								aObj = new AnimationObject(this, currentObj, isMain, this.determineDirection("EAST"),
										startX, startY, aimX, aimY);
							}

						} else if (currentObj.getPosition().getPosY() > previousObj.getPosition().getPosY()) {

							if (this.view == 0 || this.view == 2) {
								aObj = new AnimationObject(this, currentObj, isMain, this.determineDirection("SOUTH"),
										startX, startY, aimX, aimY);
							} else {
								aObj = new AnimationObject(this, currentObj, isMain, this.determineDirection("NORTH"),
										startX, startY, aimX, aimY);
							}

						} else {

							if (this.view == 0 || this.view == 2) {
								aObj = new AnimationObject(this, currentObj, isMain, this.determineDirection("NORTH"),
										startX, startY, aimX, aimY);
							} else {
								aObj = new AnimationObject(this, currentObj, isMain, this.determineDirection("SOUTH"),
										startX, startY, aimX, aimY);
							}
						}

						//Player is animating. Maining needed so that the player doesn't "overshoot" due to
						//slower animation.
						if (aObj.isMainPlayer()) {
							this.animating = true;
							this.rightFoot = !this.rightFoot; //Toggle the right foot boolean.
							aObj.setRightFoot(this.rightFoot); //Set foot as the next foot.
						}
						this.toAnimate.add(aObj);
					}
				}
			}
		}
	}

	/**
	 * Display the current room name in the header of the overlay panel.
	 */
	public void displayRoomName() {

		if (this.currentArea.getAreaName().contains("Spawn")) {
			if (this.currentArea.hasOwner()) {
				overlayPanel.setHeaderMessage(currentArea.getOwner().getPlayerName() + "'s Room");
			} else {
				overlayPanel.setHeaderMessage(currentArea.getAreaName().replace('_', ' '));
			}

		} else {
			overlayPanel.setHeaderMessage(currentArea.getAreaName().replace('_', ' '));
		}
	}

	/**
	 * Centers the player in the window
	 */
	public void centerPlayer() {
		int playerXPos = mainPlayer.getPosition().getPosX();
		int playerYPos = mainPlayer.getPosition().getPosY();

		int[] view = getRotatedView(playerXPos, playerYPos, currentArea.width, currentArea.height);
		int viewX = view[0];
		int viewY = view[1];

		int playerX = (viewX * getTileWidth()) + getTileWidth() / 2;
		int playerY = (viewY * getTileHeight()) + getTileHeight() / 2;

		int windowCenterX = (this.windowWidth / 2) + this.windowOffSetX;
		int windowCenterY = (this.windowHeight / 2) + this.windowOffSetY;

		this.renderOffSetX = (windowCenterX - playerX) - getMainPlayerXBuff();
		this.renderOffSetY = (windowCenterY - playerY) - getMainPlayerYBuff();
	}

	/**
	 * Centers the player during the animation.
	 * @param startX the main player's actual x position on the panel
	 * @param startY the main player's actual y position on the panel
	 */
	private void centerPlayerAnimation(int startX, int startY) {
		int viewX = startX;
		int viewY = startY;

		int playerX = (viewX * getTileWidth()) + getTileWidth() / 2;
		int playerY = (viewY * getTileHeight()) + getTileHeight() / 2;

		int windowCenterX = (this.windowWidth / 2) + this.windowOffSetX;
		int windowCenterY = (this.windowHeight / 2) + this.windowOffSetY;

		this.renderOffSetX = (windowCenterX - playerX) - getMainPlayerXBuff();
		this.renderOffSetY = (windowCenterY - playerY) - getMainPlayerYBuff();
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);

		//Double buffering to reduce flickering.
		Image offScreen = createImage(600, 500);
		Graphics offgc = offScreen.getGraphics();

		// add underlay
		offgc.setColor(Color.black);
		offgc.fillRect(this.windowOffSetX, this.windowOffSetY, this.windowWidth, this.windowHeight);

		if (currentArea != null)
			if (currentArea.getAreaName().equals("Outside")) {
				Image image = spriteMap.getImage(getRotatedToken("G0"));
				offgc.drawImage(image, this.renderOffSetX - ((image.getWidth(null) - this.windowWidth) / 2),
						this.renderOffSetY - ((image.getHeight(null) - this.windowHeight) / 2), null);
			}

		renderArray(offgc, 0); // render floor tiles
		renderArray(offgc, 1); // render far walls
		renderArray(offgc, 2); // render gameObjects
		renderArray(offgc, 3); // render close and side walls

		if (currentArea != null && currentArea.getAreaName().equals("Outside")) {
			if (Math.random() < 0.98) {
				Image image = spriteMap.getImage(getRotatedToken("N0"));
				offgc.drawImage(image, this.renderOffSetX - ((image.getWidth(null) - this.windowWidth) / 2),
						this.renderOffSetY - ((image.getHeight(null) - this.windowHeight) / 2), null);

				offgc.drawImage(spriteMap.getImage("Rain" + this.nextRain()), 0, 0, 600, 600, null);
			}
		}

		g.drawImage(offScreen, 0, 0, this);

	}

	/**
	 * Iterates through the array in the appropriate direction
	 * depending on the current view. Only renders the suggested layer
	 *
	 * @param g - graphics
	 * @param layer - layer to render
	 */
	public void renderArray(Graphics g, int layer) {
		if (currentArea == null)
			return;

		if (view == 0) {
			for (int y = 0; y < currentArea.height; y++)
				for (int x = 0; x < currentArea.width; x++)
					renderTile(g, layer, x, y);

		} else if (view == 1) {
			for (int x = 0; x < currentArea.width; x++)
				for (int y = currentArea.height - 1; y >= 0; y--)
					renderTile(g, layer, x, y);

		} else if (view == 2) {
			for (int y = currentArea.height - 1; y >= 0; y--)
				for (int x = currentArea.width - 1; x >= 0; x--)
					renderTile(g, layer, x, y);

		} else if (view == 3) {
			for (int x = currentArea.width - 1; x >= 0; x--)
				for (int y = 0; y < currentArea.height; y++)
					renderTile(g, layer, x, y);

		}
	}

	/**
	 * Render the the tile and its occupant
	 * @param g Graphics
	 * @param layer which layer to draw. E.g. far walls, close walls, game objects etc.
	 * @param x position on the area 2d array
	 * @param y position on the area 2d array
	 */
	public void renderTile(Graphics g, int layer, int x, int y) {

		Tile tile = currentArea.getTile(new Position(x, y));

		int[] view = getRotatedView(x, y, currentArea.width, currentArea.height);
		int viewX = view[0];
		int viewY = view[1];

		int finalX = this.renderOffSetX + viewX * getTileWidth();
		int finalY = this.renderOffSetY + viewY * getTileHeight();

		if (tile == null)
			return;

		String token = getRotatedToken(tile.getToken()); // Determine the rotated token

		// Draw floor tile
		if (layer == 0) {
			if (tile instanceof FloorTile)
				g.drawImage(spriteMap.getImage(token), finalX, finalY, null);
		}

		// Draw GameObjects(including player)
		else if (layer == 2) {
			int adjustX = 0;
			int adjustY = 0;
			Image tileImage = null;

			GameObject roomObj = this.currentArea.getTile(new Position(x, y)).getOccupant();

			if (roomObj == null) {
				return;
			}

			// if object is a door/window
			if (roomObj instanceof DoorGO) {
				DoorGO door = (DoorGO) roomObj;
				Position doorPos = door.getPosition(this.mainPlayer.getCurrentArea().getAreaName());
				if (doorPos.getPosX() == x && doorPos.getPosY() == y) {
					tileImage = spriteMap.getImage(getAnimatedDoorToken(
							door.getToken(this.mainPlayer.getCurrentArea().getAreaName()), door.isOpen()));
					adjustX = (tileImage.getWidth(null) / 2);
					adjustY = (tileImage.getHeight(null) / 2);
				}

				// if object is a container(chest)
			} else if (roomObj instanceof FixedContainerGO) {
				FixedContainerGO container = (FixedContainerGO) roomObj;
				Position containerPos = container.getPosition();
				if (containerPos.getPosX() == x && containerPos.getPosY() == y) {
					tileImage = spriteMap.getImage(getAnimatedDoorToken(container.getToken(), container.isOpen()));
					adjustX = (tileImage.getWidth(null) / 2);
					adjustY = (tileImage.getHeight(null) / 2);
				}

				// maker is a game
			} else if ((!(roomObj instanceof MarkerGO)) && roomObj.getPosition().getPosX() == x
					&& roomObj.getPosition().getPosY() == y) {

				if (roomObj instanceof Player) {
					Player p = (Player) roomObj;

					tileImage = spriteMap.getImage(getRotatedAnimatedToken(roomObj.getToken(), p.getDirection()));

					adjustX = (tileImage.getWidth(null) - getTileWidth());
					adjustY = (tileImage.getHeight(null) - getTileHeight());

					AnimationObject ao = null;
					int index;
					//Find the animation object that matches the playerObject.
					outer: for (index = 0; index < this.toAnimate.size(); index++) {
						if (this.toAnimate.get(index).getGameObj().getId().equals(p.getId())) {
							ao = this.toAnimate.get(index);
							break outer;
						}
					}

					//If this player needs to be animated, change final x and final y for animation.
					if (ao != null) {

						tileImage = spriteMap.getImage(getRotatedAnimatedToken(ao.getNextImgToken(), p.getDirection()));

						//Set animating to true, and center the player.
						if (ao.isMainPlayer()) {
							this.animating = true;
							ao.changeBuffs();
							this.centerPlayerAnimation(ao.getStartX(), ao.getStartY());
						}

						Position posToDraw = ao.getPosition(); //Dont need to worry since its main player.
						ao.incrementCurrent();

						finalX = posToDraw.getPosX();
						finalY = posToDraw.getPosY();

						//If the animation is complete, reset the buffer back to zero.
						if (ao.animationComplete()) {
							if (ao.isMainPlayer()) {
								this.animating = false;
								this.setMainPlayerXBuff(0);
								this.setMainPlayerYBuff(0);
								//this.repaint();
							}

							this.toAnimate.remove(index); //Remove the animation object now that animation is complete
						}
					}

				} else {
					tileImage = spriteMap.getImage(getRotatedToken(roomObj.getToken()));
					adjustX = (tileImage.getWidth(null) / 2);
					adjustY = (tileImage.getHeight(null) / 2);
				}
			}

			g.drawImage(tileImage, finalX - adjustX, finalY - adjustY, null);
		}

		// Draw Walls(Back and side walls with layer 1, front with layer 3)
		if (tile instanceof WallTile) {
			Image tileImage = spriteMap.getImage(token);
			int adjustX = tileImage.getWidth(null) - getTileWidth();
			int adjustY = tileImage.getHeight(null) - getTileHeight();

			if (token.equals("w0") || token.equals("L2") || token.equals("W1") || token.equals("W2")
					|| token.equals("F2") || token.equals("F1") || token.equals("f2") || token.equals("B0")
					|| token.equals("u0") || token.equals("L1") || token.equals("Q1") || token.equals("Q2")) {

				if (layer == 1) {
					g.drawImage(tileImage, finalX - adjustX - 1, finalY - adjustY - 1, tileImage.getWidth(null) + 2,
							tileImage.getHeight(null) + 2, null);

				}
			} else {
				if (layer == 3) {
					g.drawImage(tileImage, finalX - adjustX - 1, finalY - adjustY - 1, tileImage.getWidth(null) + 2,
							tileImage.getHeight(null) + 2, null);
				}
			}
		}

	}

	/**
	 * Determines the new x,y position(where the tile will be drawn)
	 * of a tile from it logical position and the current view
	 *
	 * @param x - player x position
	 * @param y - player y position
	 * @param width - width of the area array
	 * @param height - height of the area array
	 *
	 * @return int[] - returns coordinates as an array of size 2 where
	 * 					x = [0] and y = [1]
	 */
	public int[] getRotatedView(int x, int y, int width, int height) {
		int[] r = new int[2];
		int newX = 0, newY = 0;
		int oldX = x, oldY = y;

		for (int i = 0; i < view; i++) {
			newY = oldX;
			newX = oldY;
			newX = height - oldY - 1;
			oldX = newX;
			oldY = newY;
		}
		r[0] = oldX;
		r[1] = oldY;

		return r;
	}

	/**
	 *  Iterate the view field approriately,
	 *  must be 0 >= x >= 3
	 *
	 * @param r - particular rotation,
	 * 			  either 1 (anti-clockwise)
	 * 			  or -1 (clockwise)
	 */

	public void rotate(int r) {
		view += r;
		if (view == -1)
			view = 3;
		else if (view == 4)
			view = 0;
	}

	/**
	 * Gets the appropriate token for an doors/windows/chests dpending on the current view,
	 * the way its facing, and if its unlocked or locked
	 *
	 * @param token - original token
	 * @param unlocked
	 * @return - new token
	 */
	public String getAnimatedDoorToken(String token, boolean unlocked) {
		if (token == null) {
			return null;
		}

		if (unlocked) {
			String a = token.substring(0, token.length() - 1);
			token = a + 1;
		} else {
			String a = token.substring(0, token.length() - 1);
			token = a + 0;
		}
		for (int b = 0; b < view; b++) {
			String j = "" + token.charAt(token.length() - 1);
			String z = "" + token.charAt(token.length() - 2);
			int i = Integer.valueOf(z) + 1;
			if (i == -3)
				i = 0;
			else if (i == 4)
				i = 0;
			String a = token.substring(0, token.length() - 2);
			token = a + i + j;
		}
		return token;
	}

	/**
	 * Determine the approriate token string depending on the view
	 * e.g. "w1" when rotate = 1 should be "w0"
	 *
	 * @param token
	 * @return
	 */
	public String getRotatedToken(String token) {
		if (token == null)
			return null;

		for (int b = 0; b < view; b++) {

			String j = "" + token.charAt(token.length() - 1);

			int i = Integer.valueOf(j) + 1;
			if (i == -3)
				i = 0;
			else if (i == 4)
				i = 0;
			String a = token.substring(0, token.length() - 1);
			token = a + i;
		}

		return token;
	}

	/**
	 * Determine the approriate token string depending on the view
	 * e.g. "w1" when rotate = 1 should be "w0"
	 *
	 * @param token
	 * @return
	 */
	public String getRotatedAnimatedToken(String token, String direction) {
		if (token == null) {
			return null;
		}

		String sub = token.substring(0, token.length() - 2);
		String x = "" + token.charAt(token.length() - 1);
		switch (direction) {

		case "NORTH":
			token = sub + 2 + x;
			break;

		case "EAST":
			token = sub + 3 + x;
			break;

		case "SOUTH":
			token = sub + 0 + x;
			break;

		case "WEST":
			token = sub + 1 + x;
			break;
		}

		for (int b = 0; b < view; b++) {
			String j = "" + token.charAt(token.length() - 1);
			String z = "" + token.charAt(token.length() - 2);
			int i = Integer.valueOf(z) + 1;
			if (i == -3)
				i = 0;
			else if (i == 4)
				i = 0;
			String a = token.substring(0, token.length() - 2);
			token = a + i + j;
		}
		return token;
	}

	/**
	 * Determine the correct direction depending on the
	 * current rotation of the display
	 *
	 * @param direction - 2d direction
	 * @return direction - direction the user will see
	 */
	public String determineDirection(String direction) {
		for (int i = 0; i < view; i++) {
			direction = rotateDirection(direction, -1);
		}
		return direction;
	}

	/**
	 * Determine the direction in 90 degree rotation
	 * either left of right
	 *
	 * @param direction
	 * @param i - either positive(anti-Clockwise) or negative(clockwise)
	 * @return - rotated direction
	 * 		   - null(when passed 0) - shouldn't happen
	 */
	public String rotateDirection(String direction, int i) {
		switch (direction) {

		case "NORTH":
			if (i > 0)
				return "EAST";
			if (i < 0)
				return "WEST";

		case "EAST":
			if (i > 0)
				return "SOUTH";
			if (i < 0)
				return "NORTH";

		case "SOUTH":
			if (i > 0)
				return "WEST";
			if (i < 0)
				return "EAST";

		case "WEST":
			if (i > 0)
				return "NORTH";
			if (i < 0)
				return "SOUTH";
		}
		return null;
	}

	/**
	 * Used for getting the next frame of the rain images. Delay is used to make sure, rain frames are not changes
	 * too fast.
	 * @return number for next rain frame.
	 */
	public int nextRain() {
		if (delay == 0) {
			this.nextRain++;
			if (this.nextRain > 7) {
				this.nextRain = 0;
			}
			delay = 5;
		}
		this.delay--;
		return this.nextRain;
	}

	@Override
	public void keyPressed(KeyEvent e) {
		int keyCode = e.getKeyCode();

		switch (keyCode) {
		case KeyEvent.VK_UP:
			if (this.animating) {
				return;
			}
			this.client.sendCommand(determineDirection("NORTH"));
			break;

		case KeyEvent.VK_DOWN:
			if (this.animating) {
				return;
			}
			this.client.sendCommand(determineDirection("SOUTH"));
			break;

		case KeyEvent.VK_LEFT:
			if (this.animating) {
				return;
			}
			this.client.sendCommand(determineDirection("WEST"));
			break;

		case KeyEvent.VK_RIGHT:
			if (this.animating) {
				return;
			}
			this.client.sendCommand(determineDirection("EAST"));
			break;

		case KeyEvent.VK_Z:
			if (this.animating) {
				return;
			}
			this.client.sendCommand("ACTION");
			break;

		case KeyEvent.VK_R:

			if (this.animating) {
				this.overlayPanel.setFooterMessage("Cannot rotate while the player is moving.");
				return;
			}

			rotate(1);
			this.centerPlayer();
			break;

		case KeyEvent.VK_L:
			if (this.animating) {
				this.overlayPanel.setFooterMessage("Cannot rotate while the player is moving.");
				return;
			}

			rotate(-1);
			this.centerPlayer();
			break;
		}
	}

	/** GETTERS AND SETTERS **/

	public int getTileHeight() {
		return tileHeight;
	}

	public int getTileWidth() {
		return tileWidth;
	}

	public int getMainPlayerYBuff() {
		return mainPlayerYBuff;
	}

	public void setMainPlayerYBuff(int mainPlayerYBuff) {
		this.mainPlayerYBuff = mainPlayerYBuff;
	}

	public int getMainPlayerXBuff() {
		return mainPlayerXBuff;
	}

	public void setMainPlayerXBuff(int mainPlayerXBuff) {
		this.mainPlayerXBuff = mainPlayerXBuff;
	}

	public int getRenderOffSetX() {
		return this.renderOffSetX;
	}

	public int getRenderOffSetY() {
		return this.renderOffSetY;
	}

	/** UNUSED LISTENER METHODS **/

	@Override
	public void keyReleased(KeyEvent e) {
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		this.requestFocus();
	}

	@Override
	public void mouseEntered(MouseEvent e) {
	}

	@Override
	public void mouseExited(MouseEvent e) {
	}

	@Override
	public void mousePressed(MouseEvent e) {
	}

	@Override
	public void mouseReleased(MouseEvent e) {
	}
}
