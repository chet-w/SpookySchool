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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import game.Area;
import game.Bundle;
import game.DoorGO;
import game.FloorTile;
import game.GameObject;
import game.MarkerGO;
import game.Player;
import game.Position;
import game.Tile;
import network.Client;

public class AreaDisplayPanel extends JPanel implements KeyListener, MouseListener {

	private OverlayPanel overlayPanel;

	// Window size and offset
	private final int windowOffSetX = 0;
	private final int windowOffSetY = 0;
	private final int windowWidth = 600; //352
	private final int windowHeight = 500;

	// Renderer Tile Size
	public final int tileWidth = 32;
	public final int tileHeight = 25;

	public int mainPlayerXBuff;
	public int mainPlayerYBuff;

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

	private Player mainPlayer;

	private Area currentArea;

	private List<GameObject> currentAreaObjects = new ArrayList<GameObject>();
	private List<GameObject> previousAreaObjects = new ArrayList<GameObject>();

	private Map<String, AnimationObject> toAnimate = new HashMap<String, AnimationObject>();


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

		this.setBackground(Color.darkGray);
		this.setFocusable(true);
		this.requestFocus();
		this.addKeyListener(this);
		this.addMouseListener(this);
		this.spriteMap = new SpriteMap();
		this.overlayPanel = new OverlayPanel(this, spriteMap);

		this.spriteMap = spriteMap;
		//overlayPanel.setBackground(Color.BLUE);

		this.setLayout(new BorderLayout());

		validate();

		this.client = client;

		this.gameFrame = gf;
	}

	public void setOverLay(OverlayPanel overlayPanel) {
		this.overlayPanel = overlayPanel;
		overlayPanel.setOpaque(false);
		this.add(overlayPanel, BorderLayout.CENTER);
	}

	/**
	 * Process the received bundle. Display game according to the bundle.
	 */
	public void processBundle(Bundle bundle) {

		this.toAnimate.clear();
		this.mainPlayer = bundle.getPlayerObj();
		this.previousAreaObjects = this.currentAreaObjects;

		if (currentArea == null) {
			this.currentArea = this.mainPlayer.getCurrentArea();
			this.currentAreaObjects = bundle.getAreaObjects();
			overlayPanel.setHeaderMessage(-155, currentArea.getAreaName());
		} else {
			String oldArea = currentArea.getAreaName();

			if (!oldArea.equals(this.mainPlayer.getCurrentArea().getAreaName())) {
				this.currentArea = this.mainPlayer.getCurrentArea();
				overlayPanel.setHeaderMessage(-155, currentArea.getAreaName());
			}
			this.currentAreaObjects = bundle.getAreaObjects();
			this.currentArea = this.mainPlayer.getCurrentArea();
		}

		//Display overlay message/
		if (bundle.getMessage() != null) {
			overlayPanel.setFooterMessage(bundle.getMessage());
		}

		this.findChanges();

		this.updateDisplay();
	}


	public void findChanges() {

		if (this.previousAreaObjects == null) {
			//Nothing to animate!
			return;
		}

		for (int i = 0; i < this.currentAreaObjects.size(); i++) {
			for (int j = 0; j < this.previousAreaObjects.size(); j++) {
				if (this.previousAreaObjects.get(j).getId().equals(this.currentAreaObjects.get(i).getId())) {

					GameObject previousObj = this.previousAreaObjects.get(j);
					GameObject currentObj = this.currentAreaObjects.get(i);


					//ASSUMING MOVEMENT IN ONLY ONE DIRECTION!!!
					if (currentObj.getPosition().getPosX() != previousObj.getPosition().getPosX()
							|| currentObj.getPosition().getPosY() != previousObj.getPosition().getPosY()) {

						int startX = previousObj.getPosition().getPosX();
						int startY = previousObj.getPosition().getPosY();

						int aimX = currentObj.getPosition().getPosX();
						int aimY = currentObj.getPosition().getPosY();

						AnimationObject aObj = null;

						if (currentObj.getPosition().getPosX() > previousObj.getPosition().getPosX()) {
							aObj = new AnimationObject(this, currentObj, "EAST", startX, startY, aimX, aimY);
							//System.out.println("Moved EAST");

						} else if (currentObj.getPosition().getPosX() < previousObj.getPosition().getPosX()) {
							aObj = new AnimationObject(this, currentObj, "WEST", startX, startY, aimX, aimY);
							//System.out.println("Moved WEST");

						} else if (currentObj.getPosition().getPosY() > previousObj.getPosition().getPosY()) {
							aObj = new AnimationObject(this, currentObj, "SOUTH", startX, startY, aimX, aimY);
							//System.out.println("Moved SOUTH");
						} else {
							aObj = new AnimationObject(this, currentObj, "NORTH", startX, startY, aimX, aimY);
							//System.out.println("Moved NORTH");
						}

						this.toAnimate.put(currentObj.getId(), aObj);

					}

				}
			}
		}

		processAnimation();

		this.updateDisplay(); //Place player in correct location!


	}


	/**
	 * Updates the board.
	 */
	public void updateDisplay() {
		centerPlayer();
		this.repaint();

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

		int playerX = (viewX * tileWidth) + tileWidth / 2;
		int playerY = (viewY * tileHeight) + tileHeight / 2;

		int windowCenterX = (this.windowWidth / 2) + this.windowOffSetX;
		int windowCenterY = (this.windowHeight / 2) + this.windowOffSetY;

		this.renderOffSetX = (windowCenterX - playerX) - mainPlayerXBuff;
		this.renderOffSetY = (windowCenterY - playerY) - mainPlayerYBuff;
	}



	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);

		/** USING SMOOTH RENDERING **/

		Image offscreen = createImage(600, 500);
		Graphics offgc = offscreen.getGraphics();

		// add underlay
		offgc.setColor(Color.black);
		offgc.fillRect(this.windowOffSetX, this.windowOffSetY, this.windowWidth, this.windowHeight);

		if (currentArea != null)
			if (currentArea.getAreaName().equals("Outside"))
				offgc.drawImage(spriteMap.getImage(getRotatedToken("G0")), (this.renderOffSetX - this.windowWidth) / 2,
						(this.renderOffSetY - this.windowHeight) / 2, null);

		renderArray(offgc, 0); // render floor tiles		
		renderArray(offgc, 1); // render far walls
		renderArray(offgc, 2); // render gameObjects
		renderArray(offgc, 3); // render close and side walls



		if (currentArea != null && currentArea.getAreaName().equals("Outside")) {
			if (Math.random() < 0.96) {
				offgc.drawImage(spriteMap.getImage(getRotatedToken("N0")), 0, 0, null);
				offgc.drawImage(spriteMap.getImage("Rain" + this.nextRain()), 0, 0, 600, 600, null);
			}
		}

		g.drawImage(offscreen, 0, 0, this);

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
	 * Only animating players for now.
	 */
	private void processAnimation() {

		int completedAnimations = 0;

		while (completedAnimations != this.toAnimate.size()) {
			for (AnimationObject ao : this.toAnimate.values()) {

				System.out.println("stating single move.");

				ao.changeBuff();
				this.updateDisplay();

				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				if (ao.animationComplete()) {
					completedAnimations++;
					System.out.println("Completed an animation");
				}
			}
		}

		this.toAnimate.clear();

		this.mainPlayerXBuff = 0;
		this.mainPlayerYBuff = 0;

	}

	public void renderTile(Graphics g, int layer, int x, int y) {

		Tile tile = currentArea.getTile(new Position(x, y));

		int[] view = getRotatedView(x, y, currentArea.width, currentArea.height);
		int viewX = view[0];
		int viewY = view[1];

		int finalX = this.renderOffSetX + viewX * tileWidth;
		int finalY = this.renderOffSetY + viewY * tileHeight;

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


			if (roomObj instanceof DoorGO) {
				DoorGO door = (DoorGO) roomObj;
				Position doorPos = door.getPosition(this.mainPlayer.getCurrentArea().getAreaName());

				//if (doorPos.getPosX() == x && doorPos.getPosY() == y) {
				tileImage = spriteMap.getImage(getAnimatedDoorToken(
						door.getToken(this.mainPlayer.getCurrentArea().getAreaName()), door.isOpen()));
				adjustX = (tileImage.getWidth(null) / 2);
				adjustY = (tileImage.getHeight(null) / 2);
				//}

			} else if ((!(roomObj instanceof MarkerGO)) && (!(roomObj instanceof Player))
			/*&& roomObj.getPosition().getPosX() == x && roomObj.getPosition().getPosY() == y*/) {

				tileImage = spriteMap.getImage(getRotatedToken(roomObj.getToken()));
				adjustX = (tileImage.getWidth(null) / 2);
				adjustY = (tileImage.getHeight(null) / 2);

			} else if (roomObj instanceof Player) {

				//THIS PART ASSUMES ANIMATION FOR PLAYER - FOR NOW....

				//Get the game object on the tile.
				Player p = (Player) roomObj;
				tileImage = spriteMap.getImage(getRotatedAnimatedToken(roomObj.getToken(), p.getDirection()));


				if (this.toAnimate.get(p.getId()) != null) {
					AnimationObject ao = this.toAnimate.get(p.getId());

					tileImage = spriteMap.getImage(getRotatedAnimatedToken(ao.getNextImgToken(), p.getDirection()));
				}

				adjustX = (tileImage.getWidth(null) - tileWidth) - mainPlayerXBuff;
				adjustY = (tileImage.getHeight(null) - tileHeight) - mainPlayerYBuff;
			}

			g.drawImage(tileImage, finalX - adjustX, finalY - adjustY, null);
		}

		// Draw Walls(Back and side walls with layer 1, front with layer 3)

		if (((token.equals("w0") || token.equals("W1") || token.equals("F2") || token.equals("F1") || token.equals("f2")
				|| token.equals("B0") || token.equals("Q1") || token.equals("Q2")) && layer == 1)
				|| ((!(token.equals("w0") || token.equals("W1") || token.equals("W2") || token.equals("f2")
						|| token.equals("F1") || token.equals("F2") || token.equals("B0") || token.equals("Q1")
						|| token.equals("Q2"))) && layer == 3))

		{
			if (token.contains("w") || token.contains("W") || token.contains("B") || token.contains("Q")
					|| token.contains("f") || token.contains("F")) {
				Image tileImage = spriteMap.getImage(token);
				int adjustX = tileImage.getWidth(null) - tileWidth;
				int adjustY = tileImage.getHeight(null) - tileHeight;
				g.drawImage(tileImage, finalX - adjustX, finalY - adjustY, null);
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


	public int getRenderOffSetX() {
		return renderOffSetX;
	}

	public int getRenderOffSetY() {
		return renderOffSetY;
	}


	@Override
	public void keyPressed(KeyEvent e) {
		int keyCode = e.getKeyCode();

		switch (keyCode) {
		case KeyEvent.VK_UP:
			this.client.sendCommand(determineDirection("NORTH"));
			break;
		case KeyEvent.VK_DOWN:
			this.client.sendCommand(determineDirection("SOUTH"));
			break;
		case KeyEvent.VK_LEFT:
			this.client.sendCommand(determineDirection("WEST"));
			break;
		case KeyEvent.VK_RIGHT:
			this.client.sendCommand(determineDirection("EAST"));
			break;
		case KeyEvent.VK_Z:
			this.client.sendCommand("ACTION");
			break;
		case KeyEvent.VK_S:
			this.client.sendCommand("SAVE");
			break;
		case KeyEvent.VK_R:
			rotate(1);
			this.updateDisplay();
			break;
		case KeyEvent.VK_L:
			rotate(-1);
			this.updateDisplay();
			break;
		}
	}


	@Override
	public void keyReleased(KeyEvent e) {}


	@Override
	public void keyTyped(KeyEvent e) {}


	@Override
	public void mouseClicked(MouseEvent e) {
		this.requestFocus();
	}


	@Override
	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub

	}


	@Override
	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub

	}


	@Override
	public void mousePressed(MouseEvent e) {
		// TODO Auto-generated method stub

	}


	@Override
	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub

	}

}
