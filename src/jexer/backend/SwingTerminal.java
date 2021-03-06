/*
 * Jexer - Java Text User Interface
 *
 * The MIT License (MIT)
 *
 * Copyright (C) 2017 Kevin Lamonte
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * @author Kevin Lamonte [kevin.lamonte@gmail.com]
 * @version 1
 */
package jexer.backend;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import jexer.TKeypress;
import jexer.bits.Cell;
import jexer.bits.CellAttributes;
import jexer.event.TCommandEvent;
import jexer.event.TInputEvent;
import jexer.event.TKeypressEvent;
import jexer.event.TMouseEvent;
import jexer.event.TResizeEvent;
import static jexer.TCommand.*;
import static jexer.TKeypress.*;

/**
 * This Screen backend reads keystrokes and mouse events and draws to either
 * a Java Swing JFrame (potentially triple-buffered) or a JComponent.
 *
 * This class is a bit of an inversion of typical GUI classes.  It performs
 * all of the drawing logic from SwingTerminal (which is not a Swing class),
 * and uses a SwingComponent wrapper class to call the JFrame or JComponent
 * methods.
 */
public class SwingTerminal extends LogicalScreen
                           implements TerminalReader,
                                      ComponentListener, KeyListener,
                                      MouseListener, MouseMotionListener,
                                      MouseWheelListener, WindowListener {

    // ------------------------------------------------------------------------
    // Constants --------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * The icon image location.
     */
    private static final String ICONFILE = "jexer_logo_128.png";

    /**
     * The terminus font resource filename.
     */
    private static final String FONTFILE = "terminus-ttf-4.39/TerminusTTF-Bold-4.39.ttf";

    /**
     * Cursor style to draw.
     */
    public enum CursorStyle {
        /**
         * Use an underscore for the cursor.
         */
        UNDERLINE,

        /**
         * Use a solid block for the cursor.
         */
        BLOCK,

        /**
         * Use an outlined block for the cursor.
         */
        OUTLINE
    }

    // ------------------------------------------------------------------------
    // Variables --------------------------------------------------------------
    // ------------------------------------------------------------------------

    // Colors to map DOS colors to AWT colors.
    private static Color MYBLACK;
    private static Color MYRED;
    private static Color MYGREEN;
    private static Color MYYELLOW;
    private static Color MYBLUE;
    private static Color MYMAGENTA;
    private static Color MYCYAN;
    private static Color MYWHITE;
    private static Color MYBOLD_BLACK;
    private static Color MYBOLD_RED;
    private static Color MYBOLD_GREEN;
    private static Color MYBOLD_YELLOW;
    private static Color MYBOLD_BLUE;
    private static Color MYBOLD_MAGENTA;
    private static Color MYBOLD_CYAN;
    private static Color MYBOLD_WHITE;

    /**
     * When true, all the MYBLACK, MYRED, etc. colors are set.
     */
    private static boolean dosColors = false;

    /**
     * The Swing component or frame to draw to.
     */
    private SwingComponent swing;

    /**
     * A cache of previously-rendered glyphs for blinking text, when it is
     * not visible.
     */
    private Map<Cell, BufferedImage> glyphCacheBlink;

    /**
     * A cache of previously-rendered glyphs for non-blinking, or
     * blinking-and-visible, text.
     */
    private Map<Cell, BufferedImage> glyphCache;

    /**
     * If true, we were successful getting Terminus.
     */
    private boolean gotTerminus = false;

    /**
     * If true, we were successful at getting the font dimensions.
     */
    private boolean gotFontDimensions = false;

    /**
     * The currently selected font.
     */
    private Font font = null;

    /**
     * The currently selected font size in points.
     */
    private int fontSize = 16;

    /**
     * Width of a character cell in pixels.
     */
    private int textWidth = 1;

    /**
     * Height of a character cell in pixels.
     */
    private int textHeight = 1;

    /**
     * Descent of a character cell in pixels.
     */
    private int maxDescent = 0;

    /**
     * System-dependent Y adjustment for text in the character cell.
     */
    private int textAdjustY = 0;

    /**
     * System-dependent X adjustment for text in the character cell.
     */
    private int textAdjustX = 0;

    /**
     * Top pixel absolute location.
     */
    private int top = 30;

    /**
     * Left pixel absolute location.
     */
    private int left = 30;

    /**
     * The cursor style to draw.
     */
    private CursorStyle cursorStyle = CursorStyle.UNDERLINE;

    /**
     * The number of millis to wait before switching the blink from visible
     * to invisible.  Set to 0 or negative to disable blinking.
     */
    private long blinkMillis = 500;

    /**
     * If true, the cursor should be visible right now based on the blink
     * time.
     */
    private boolean cursorBlinkVisible = true;

    /**
     * The time that the blink last flipped from visible to invisible or
     * from invisible to visible.
     */
    private long lastBlinkTime = 0;

    /**
     * The session information.
     */
    private SwingSessionInfo sessionInfo;

    /**
     * The listening object that run() wakes up on new input.
     */
    private Object listener;

    /**
     * The event queue, filled up by a thread reading on input.
     */
    private List<TInputEvent> eventQueue;

    /**
     * The last reported mouse X position.
     */
    private int oldMouseX = -1;

    /**
     * The last reported mouse Y position.
     */
    private int oldMouseY = -1;

    /**
     * true if mouse1 was down.  Used to report mouse1 on the release event.
     */
    private boolean mouse1 = false;

    /**
     * true if mouse2 was down.  Used to report mouse2 on the release event.
     */
    private boolean mouse2 = false;

    /**
     * true if mouse3 was down.  Used to report mouse3 on the release event.
     */
    private boolean mouse3 = false;

    // ------------------------------------------------------------------------
    // Constructors -----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Public constructor creates a new JFrame to render to.
     *
     * @param windowWidth the number of text columns to start with
     * @param windowHeight the number of text rows to start with
     * @param fontSize the size in points.  Good values to pick are: 16, 20,
     * 22, and 24.
     * @param listener the object this backend needs to wake up when new
     * input comes in
     */
    public SwingTerminal(final int windowWidth, final int windowHeight,
        final int fontSize, final Object listener) {

        this.fontSize = fontSize;

        setDOSColors();

        // Figure out my cursor style.
        String cursorStyleString = System.getProperty(
            "jexer.Swing.cursorStyle", "underline").toLowerCase();
        if (cursorStyleString.equals("underline")) {
            cursorStyle = CursorStyle.UNDERLINE;
        } else if (cursorStyleString.equals("outline")) {
            cursorStyle = CursorStyle.OUTLINE;
        } else if (cursorStyleString.equals("block")) {
            cursorStyle = CursorStyle.BLOCK;
        }

        // Pull the system property for triple buffering.
        if (System.getProperty("jexer.Swing.tripleBuffer") != null) {
            if (System.getProperty("jexer.Swing.tripleBuffer").equals("true")) {
                SwingComponent.tripleBuffer = true;
            } else {
                SwingComponent.tripleBuffer = false;
            }
        }

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {

                    JFrame frame = new JFrame() {

                        /**
                         * Serializable version.
                         */
                        private static final long serialVersionUID = 1;

                        /**
                         * The code that performs the actual drawing.
                         */
                        public SwingTerminal screen = null;

                        /*
                         * Anonymous class initializer saves the screen
                         * reference, so that paint() and the like call out
                         * to SwingTerminal.
                         */
                        {
                            this.screen = SwingTerminal.this;
                        }

                        /**
                         * Update redraws the whole screen.
                         *
                         * @param gr the Swing Graphics context
                         */
                        @Override
                        public void update(final Graphics gr) {
                            // The default update clears the area.  Don't do
                            // that, instead just paint it directly.
                            paint(gr);
                        }

                        /**
                         * Paint redraws the whole screen.
                         *
                         * @param gr the Swing Graphics context
                         */
                        @Override
                        public void paint(final Graphics gr) {
                            if (screen != null) {
                                screen.paint(gr);
                            }
                        }
                    };

                    // Set icon
                    ClassLoader loader = Thread.currentThread().
                        getContextClassLoader();
                    frame.setIconImage((new ImageIcon(loader.
                                getResource(ICONFILE))).getImage());

                    // Get the Swing component
                    SwingTerminal.this.swing = new SwingComponent(frame);

                    // Hang onto top and left for drawing.
                    Insets insets = SwingTerminal.this.swing.getInsets();
                    SwingTerminal.this.left = insets.left;
                    SwingTerminal.this.top = insets.top;

                    // Load the font so that we can set sessionInfo.
                    getDefaultFont();

                    // Get the default cols x rows and set component size
                    // accordingly.
                    SwingTerminal.this.sessionInfo =
                        new SwingSessionInfo(SwingTerminal.this.swing,
                            SwingTerminal.this.textWidth,
                            SwingTerminal.this.textHeight,
                            windowWidth, windowHeight);

                    SwingTerminal.this.setDimensions(sessionInfo.getWindowWidth(),
                        sessionInfo.getWindowHeight());

                    SwingTerminal.this.resizeToScreen();
                    SwingTerminal.this.swing.setVisible(true);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.listener    = listener;
        mouse1           = false;
        mouse2           = false;
        mouse3           = false;
        eventQueue       = new LinkedList<TInputEvent>();

        // Add listeners to Swing.
        swing.addKeyListener(this);
        swing.addWindowListener(this);
        swing.addComponentListener(this);
        swing.addMouseListener(this);
        swing.addMouseMotionListener(this);
        swing.addMouseWheelListener(this);
    }

    /**
     * Public constructor renders to an existing JComponent.
     *
     * @param component the Swing component to render to
     * @param windowWidth the number of text columns to start with
     * @param windowHeight the number of text rows to start with
     * @param fontSize the size in points.  Good values to pick are: 16, 20,
     * 22, and 24.
     * @param listener the object this backend needs to wake up when new
     * input comes in
     */
    public SwingTerminal(final JComponent component, final int windowWidth,
        final int windowHeight, final int fontSize, final Object listener) {

        this.fontSize = fontSize;

        setDOSColors();

        // Figure out my cursor style.
        String cursorStyleString = System.getProperty(
            "jexer.Swing.cursorStyle", "underline").toLowerCase();
        if (cursorStyleString.equals("underline")) {
            cursorStyle = CursorStyle.UNDERLINE;
        } else if (cursorStyleString.equals("outline")) {
            cursorStyle = CursorStyle.OUTLINE;
        } else if (cursorStyleString.equals("block")) {
            cursorStyle = CursorStyle.BLOCK;
        }

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {

                    JComponent newComponent = new JComponent() {

                        /**
                         * Serializable version.
                         */
                        private static final long serialVersionUID = 1;

                        /**
                         * The code that performs the actual drawing.
                         */
                        public SwingTerminal screen = null;

                        /*
                         * Anonymous class initializer saves the screen
                         * reference, so that paint() and the like call out
                         * to SwingTerminal.
                         */
                        {
                            this.screen = SwingTerminal.this;
                        }

                        /**
                         * Update redraws the whole screen.
                         *
                         * @param gr the Swing Graphics context
                         */
                        @Override
                        public void update(final Graphics gr) {
                            // The default update clears the area.  Don't do
                            // that, instead just paint it directly.
                            paint(gr);
                        }

                        /**
                         * Paint redraws the whole screen.
                         *
                         * @param gr the Swing Graphics context
                         */
                        @Override
                        public void paint(final Graphics gr) {
                            if (screen != null) {
                                screen.paint(gr);
                            }
                        }
                    };
                    component.setLayout(new BorderLayout());
                    component.add(newComponent);

                    // Allow key events to be received
                    component.setFocusable(true);

                    // Get the Swing component
                    SwingTerminal.this.swing = new SwingComponent(component);

                    // Hang onto top and left for drawing.
                    Insets insets = SwingTerminal.this.swing.getInsets();
                    SwingTerminal.this.left = insets.left;
                    SwingTerminal.this.top = insets.top;

                    // Load the font so that we can set sessionInfo.
                    getDefaultFont();

                    // Get the default cols x rows and set component size
                    // accordingly.
                    SwingTerminal.this.sessionInfo =
                        new SwingSessionInfo(SwingTerminal.this.swing,
                            SwingTerminal.this.textWidth,
                            SwingTerminal.this.textHeight);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.listener    = listener;
        mouse1           = false;
        mouse2           = false;
        mouse3           = false;
        eventQueue       = new LinkedList<TInputEvent>();

        // Add listeners to Swing.
        swing.addKeyListener(this);
        swing.addWindowListener(this);
        swing.addComponentListener(this);
        swing.addMouseListener(this);
        swing.addMouseMotionListener(this);
        swing.addMouseWheelListener(this);
    }

    // ------------------------------------------------------------------------
    // LogicalScreen ----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Set the window title.
     *
     * @param title the new title
     */
    @Override
    public void setTitle(final String title) {
        swing.setTitle(title);
    }

    /**
     * Push the logical screen to the physical device.
     */
    @Override
    public void flushPhysical() {
        // See if it is time to flip the blink time.
        long nowTime = System.currentTimeMillis();
        if (nowTime >= blinkMillis + lastBlinkTime) {
            lastBlinkTime = nowTime;
            cursorBlinkVisible = !cursorBlinkVisible;
            // System.err.println("New lastBlinkTime: " + lastBlinkTime);
        }

        if ((swing.getFrame() != null)
            && (swing.getBufferStrategy() != null)
        ) {
            do {
                do {
                    drawToSwing();
                } while (swing.getBufferStrategy().contentsRestored());

                swing.getBufferStrategy().show();
                Toolkit.getDefaultToolkit().sync();
            } while (swing.getBufferStrategy().contentsLost());

        } else {
            // Non-triple-buffered, call drawToSwing() once
            drawToSwing();
        }
    }

    // ------------------------------------------------------------------------
    // TerminalReader ---------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Check if there are events in the queue.
     *
     * @return if true, getEvents() has something to return to the backend
     */
    public boolean hasEvents() {
        synchronized (eventQueue) {
            return (eventQueue.size() > 0);
        }
    }

    /**
     * Return any events in the IO queue.
     *
     * @param queue list to append new events to
     */
    public void getEvents(final List<TInputEvent> queue) {
        synchronized (eventQueue) {
            if (eventQueue.size() > 0) {
                synchronized (queue) {
                    queue.addAll(eventQueue);
                }
                eventQueue.clear();
            }
        }
    }

    /**
     * Restore terminal to normal state.
     */
    public void closeTerminal() {
        shutdown();
    }

    /**
     * Set listener to a different Object.
     *
     * @param listener the new listening object that run() wakes up on new
     * input
     */
    public void setListener(final Object listener) {
        this.listener = listener;
    }

    // ------------------------------------------------------------------------
    // SwingTerminal ----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Setup Swing colors to match DOS color palette.
     */
    private static void setDOSColors() {
        if (dosColors) {
            return;
        }
        MYBLACK         = new Color(0x00, 0x00, 0x00);
        MYRED           = new Color(0xa8, 0x00, 0x00);
        MYGREEN         = new Color(0x00, 0xa8, 0x00);
        MYYELLOW        = new Color(0xa8, 0x54, 0x00);
        MYBLUE          = new Color(0x00, 0x00, 0xa8);
        MYMAGENTA       = new Color(0xa8, 0x00, 0xa8);
        MYCYAN          = new Color(0x00, 0xa8, 0xa8);
        MYWHITE         = new Color(0xa8, 0xa8, 0xa8);
        MYBOLD_BLACK    = new Color(0x54, 0x54, 0x54);
        MYBOLD_RED      = new Color(0xfc, 0x54, 0x54);
        MYBOLD_GREEN    = new Color(0x54, 0xfc, 0x54);
        MYBOLD_YELLOW   = new Color(0xfc, 0xfc, 0x54);
        MYBOLD_BLUE     = new Color(0x54, 0x54, 0xfc);
        MYBOLD_MAGENTA  = new Color(0xfc, 0x54, 0xfc);
        MYBOLD_CYAN     = new Color(0x54, 0xfc, 0xfc);
        MYBOLD_WHITE    = new Color(0xfc, 0xfc, 0xfc);

        dosColors = true;
    }

    /**
     * Get the number of millis to wait before switching the blink from
     * visible to invisible.
     *
     * @return the number of milli to wait before switching the blink from
     * visible to invisible
     */
    public long getBlinkMillis() {
        return blinkMillis;
    }

    /**
     * Get the font size in points.
     *
     * @return font size in points
     */
    public int getFontSize() {
        return fontSize;
    }

    /**
     * Set the font size in points.
     *
     * @param fontSize font size in points
     */
    public void setFontSize(final int fontSize) {
        this.fontSize = fontSize;
        Font newFont = font.deriveFont((float) fontSize);
        setFont(newFont);
    }

    /**
     * Set to a new font, and resize the screen to match its dimensions.
     *
     * @param font the new font
     */
    public void setFont(final Font font) {
        this.font = font;
        getFontDimensions();
        swing.setFont(font);
        glyphCacheBlink = new HashMap<Cell, BufferedImage>();
        glyphCache = new HashMap<Cell, BufferedImage>();
        resizeToScreen();
    }

    /**
     * Set the font to Terminus, the best all-around font for both CP437 and
     * ISO8859-1.
     */
    public void getDefaultFont() {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            InputStream in = loader.getResourceAsStream(FONTFILE);
            Font terminusRoot = Font.createFont(Font.TRUETYPE_FONT, in);
            Font terminus = terminusRoot.deriveFont(Font.PLAIN, fontSize);
            gotTerminus = true;
            font = terminus;
        } catch (Exception e) {
            e.printStackTrace();
            font = new Font(Font.MONOSPACED, Font.PLAIN, fontSize);
        }

        setFont(font);
    }

    /**
     * Convert a CellAttributes foreground color to an Swing Color.
     *
     * @param attr the text attributes
     * @return the Swing Color
     */
    private Color attrToForegroundColor(final CellAttributes attr) {
        int rgb = attr.getForeColorRGB();
        if (rgb >= 0) {
            int red     = (rgb >> 16) & 0xFF;
            int green   = (rgb >>  8) & 0xFF;
            int blue    =  rgb        & 0xFF;

            return new Color(red, green, blue);
        }

        if (attr.isBold()) {
            if (attr.getForeColor().equals(jexer.bits.Color.BLACK)) {
                return MYBOLD_BLACK;
            } else if (attr.getForeColor().equals(jexer.bits.Color.RED)) {
                return MYBOLD_RED;
            } else if (attr.getForeColor().equals(jexer.bits.Color.BLUE)) {
                return MYBOLD_BLUE;
            } else if (attr.getForeColor().equals(jexer.bits.Color.GREEN)) {
                return MYBOLD_GREEN;
            } else if (attr.getForeColor().equals(jexer.bits.Color.YELLOW)) {
                return MYBOLD_YELLOW;
            } else if (attr.getForeColor().equals(jexer.bits.Color.CYAN)) {
                return MYBOLD_CYAN;
            } else if (attr.getForeColor().equals(jexer.bits.Color.MAGENTA)) {
                return MYBOLD_MAGENTA;
            } else if (attr.getForeColor().equals(jexer.bits.Color.WHITE)) {
                return MYBOLD_WHITE;
            }
        } else {
            if (attr.getForeColor().equals(jexer.bits.Color.BLACK)) {
                return MYBLACK;
            } else if (attr.getForeColor().equals(jexer.bits.Color.RED)) {
                return MYRED;
            } else if (attr.getForeColor().equals(jexer.bits.Color.BLUE)) {
                return MYBLUE;
            } else if (attr.getForeColor().equals(jexer.bits.Color.GREEN)) {
                return MYGREEN;
            } else if (attr.getForeColor().equals(jexer.bits.Color.YELLOW)) {
                return MYYELLOW;
            } else if (attr.getForeColor().equals(jexer.bits.Color.CYAN)) {
                return MYCYAN;
            } else if (attr.getForeColor().equals(jexer.bits.Color.MAGENTA)) {
                return MYMAGENTA;
            } else if (attr.getForeColor().equals(jexer.bits.Color.WHITE)) {
                return MYWHITE;
            }
        }
        throw new IllegalArgumentException("Invalid color: " +
            attr.getForeColor().getValue());
    }

    /**
     * Convert a CellAttributes background color to an Swing Color.
     *
     * @param attr the text attributes
     * @return the Swing Color
     */
    private Color attrToBackgroundColor(final CellAttributes attr) {
        int rgb = attr.getBackColorRGB();
        if (rgb >= 0) {
            int red     = (rgb >> 16) & 0xFF;
            int green   = (rgb >>  8) & 0xFF;
            int blue    =  rgb        & 0xFF;

            return new Color(red, green, blue);
        }

        if (attr.getBackColor().equals(jexer.bits.Color.BLACK)) {
            return MYBLACK;
        } else if (attr.getBackColor().equals(jexer.bits.Color.RED)) {
            return MYRED;
        } else if (attr.getBackColor().equals(jexer.bits.Color.BLUE)) {
            return MYBLUE;
        } else if (attr.getBackColor().equals(jexer.bits.Color.GREEN)) {
            return MYGREEN;
        } else if (attr.getBackColor().equals(jexer.bits.Color.YELLOW)) {
            return MYYELLOW;
        } else if (attr.getBackColor().equals(jexer.bits.Color.CYAN)) {
            return MYCYAN;
        } else if (attr.getBackColor().equals(jexer.bits.Color.MAGENTA)) {
            return MYMAGENTA;
        } else if (attr.getBackColor().equals(jexer.bits.Color.WHITE)) {
            return MYWHITE;
        }
        throw new IllegalArgumentException("Invalid color: " +
            attr.getBackColor().getValue());
    }

    /**
     * Figure out what textAdjustX and textAdjustY should be, based on the
     * location of a vertical bar (to find textAdjustY) and a horizontal bar
     * (to find textAdjustX).
     *
     * @return true if textAdjustX and textAdjustY were guessed at correctly
     */
    private boolean getFontAdjustments() {
        BufferedImage image = null;

        // What SHOULD happen is that the topmost/leftmost white pixel is at
        // position (gr2x, gr2y).  But it might also be off by a pixel in
        // either direction.

        Graphics2D gr2 = null;
        int gr2x = 3;
        int gr2y = 3;
        image = new BufferedImage(textWidth * 2, textHeight * 2,
            BufferedImage.TYPE_INT_ARGB);

        gr2 = image.createGraphics();
        gr2.setFont(swing.getFont());
        gr2.setColor(java.awt.Color.BLACK);
        gr2.fillRect(0, 0, textWidth * 2, textHeight * 2);
        gr2.setColor(java.awt.Color.WHITE);
        char [] chars = new char[1];
        chars[0] = jexer.bits.GraphicsChars.VERTICAL_BAR;
        gr2.drawChars(chars, 0, 1, gr2x, gr2y + textHeight - maxDescent);
        gr2.dispose();

        for (int x = 0; x < textWidth; x++) {
            for (int y = 0; y < textHeight; y++) {

                /*
                System.err.println("X: " + x + " Y: " + y + " " +
                    image.getRGB(x, y));
                 */

                if ((image.getRGB(x, y) & 0xFFFFFF) != 0) {
                    textAdjustY = (gr2y - y);

                    // System.err.println("textAdjustY: " + textAdjustY);
                    x = textWidth;
                    break;
                }
            }
        }

        gr2 = image.createGraphics();
        gr2.setFont(swing.getFont());
        gr2.setColor(java.awt.Color.BLACK);
        gr2.fillRect(0, 0, textWidth * 2, textHeight * 2);
        gr2.setColor(java.awt.Color.WHITE);
        chars[0] = jexer.bits.GraphicsChars.SINGLE_BAR;
        gr2.drawChars(chars, 0, 1, gr2x, gr2y + textHeight - maxDescent);
        gr2.dispose();

        for (int x = 0; x < textWidth; x++) {
            for (int y = 0; y < textHeight; y++) {

                /*
                System.err.println("X: " + x + " Y: " + y + " " +
                    image.getRGB(x, y));
                 */

                if ((image.getRGB(x, y) & 0xFFFFFF) != 0) {
                    textAdjustX = (gr2x - x);

                    // System.err.println("textAdjustX: " + textAdjustX);
                    return true;
                }
            }
        }

        // Something weird happened, don't rely on this function.
        // System.err.println("getFontAdjustments: false");
        return false;
    }

    /**
     * Figure out my font dimensions.  This code path works OK for the JFrame
     * case, and can be called immediately after JFrame creation.
     */
    private void getFontDimensions() {
        swing.setFont(font);
        Graphics gr = swing.getGraphics();
        if (gr == null) {
            return;
        }
        getFontDimensions(gr);
    }

    /**
     * Figure out my font dimensions.  This code path is needed to lazy-load
     * the information inside paint().
     *
     * @param gr Graphics object to use
     */
    private void getFontDimensions(final Graphics gr) {
        swing.setFont(font);
        FontMetrics fm = gr.getFontMetrics();
        maxDescent = fm.getMaxDescent();
        Rectangle2D bounds = fm.getMaxCharBounds(gr);
        int leading = fm.getLeading();
        textWidth = (int)Math.round(bounds.getWidth());
        // textHeight = (int)Math.round(bounds.getHeight()) - maxDescent;

        // This produces the same number, but works better for ugly
        // monospace.
        textHeight = fm.getMaxAscent() + maxDescent - leading;

        if (gotTerminus == true) {
            textHeight++;
        }

        if (getFontAdjustments() == false) {
            // We were unable to programmatically determine textAdjustX and
            // textAdjustY, so try some guesses based on VM vendor.
            String runtime = System.getProperty("java.runtime.name");
            if ((runtime != null) && (runtime.contains("Java(TM)"))) {
                textAdjustY = -1;
                textAdjustX = 0;
            }
        }

        if (sessionInfo != null) {
            sessionInfo.setTextCellDimensions(textWidth, textHeight);
        }
        gotFontDimensions = true;
    }

    /**
     * Resize to font dimensions.
     */
    public void resizeToScreen() {
        swing.setDimensions(textWidth * width, textHeight * height);
    }

    /**
     * Draw one glyph to the screen.
     *
     * @param gr the Swing Graphics context
     * @param cell the Cell to draw
     * @param xPixel the x-coordinate to render to.  0 means the
     * left-most pixel column.
     * @param yPixel the y-coordinate to render to.  0 means the top-most
     * pixel row.
     */
    private void drawGlyph(final Graphics gr, final Cell cell,
        final int xPixel, final int yPixel) {

        /*
        System.err.println("drawGlyph(): " + xPixel + " " + yPixel +
            " " + cell);
        */

        BufferedImage image = null;
        if (cell.isBlink() && !cursorBlinkVisible) {
            image = glyphCacheBlink.get(cell);
        } else {
            image = glyphCache.get(cell);
        }
        if (image != null) {
            if (swing.getFrame() != null) {
                gr.drawImage(image, xPixel, yPixel, swing.getFrame());
            } else {
                gr.drawImage(image, xPixel, yPixel, swing.getComponent());
            }
            return;
        }

        // Generate glyph and draw it.
        Graphics2D gr2 = null;
        int gr2x = xPixel;
        int gr2y = yPixel;
        if ((SwingComponent.tripleBuffer) && (swing.getFrame() != null)) {
            image = new BufferedImage(textWidth, textHeight,
                BufferedImage.TYPE_INT_ARGB);
            gr2 = image.createGraphics();
            gr2.setFont(swing.getFont());
            gr2x = 0;
            gr2y = 0;
        } else {
            gr2 = (Graphics2D) gr;
        }

        Cell cellColor = new Cell();
        cellColor.setTo(cell);

        // Check for reverse
        if (cell.isReverse()) {
            cellColor.setForeColor(cell.getBackColor());
            cellColor.setBackColor(cell.getForeColor());
        }

        // Draw the background rectangle, then the foreground character.
        gr2.setColor(attrToBackgroundColor(cellColor));
        gr2.fillRect(gr2x, gr2y, textWidth, textHeight);

        // Handle blink and underline
        if (!cell.isBlink()
            || (cell.isBlink() && cursorBlinkVisible)
        ) {
            gr2.setColor(attrToForegroundColor(cellColor));
            char [] chars = new char[1];
            chars[0] = cell.getChar();
            gr2.drawChars(chars, 0, 1, gr2x + textAdjustX,
                gr2y + textHeight - maxDescent + textAdjustY);

            if (cell.isUnderline()) {
                gr2.fillRect(gr2x, gr2y + textHeight - 2, textWidth, 2);
            }
        }

        if ((SwingComponent.tripleBuffer) && (swing.getFrame() != null)) {
            gr2.dispose();

            // We need a new key that will not be mutated by
            // invertCell().
            Cell key = new Cell();
            key.setTo(cell);
            if (cell.isBlink() && !cursorBlinkVisible) {
                glyphCacheBlink.put(key, image);
            } else {
                glyphCache.put(key, image);
            }

            if (swing.getFrame() != null) {
                gr.drawImage(image, xPixel, yPixel, swing.getFrame());
            } else {
                gr.drawImage(image, xPixel, yPixel, swing.getComponent());
            }
        }

    }

    /**
     * Check if the cursor is visible, and if so draw it.
     *
     * @param gr the Swing Graphics context
     */
    private void drawCursor(final Graphics gr) {

        if (cursorVisible
            && (cursorY >= 0)
            && (cursorX >= 0)
            && (cursorY <= height - 1)
            && (cursorX <= width - 1)
            && cursorBlinkVisible
        ) {
            int xPixel = cursorX * textWidth + left;
            int yPixel = cursorY * textHeight + top;
            Cell lCell = logical[cursorX][cursorY];
            gr.setColor(attrToForegroundColor(lCell));
            switch (cursorStyle) {
            default:
                // Fall through...
            case UNDERLINE:
                gr.fillRect(xPixel, yPixel + textHeight - 2, textWidth, 2);
                break;
            case BLOCK:
                gr.fillRect(xPixel, yPixel, textWidth, textHeight);
                break;
            case OUTLINE:
                gr.drawRect(xPixel, yPixel, textWidth - 1, textHeight - 1);
                break;
            }
        }
    }

    /**
     * Reset the blink timer.
     */
    private void resetBlinkTimer() {
        lastBlinkTime = System.currentTimeMillis();
        cursorBlinkVisible = true;
    }

    /**
     * Paint redraws the whole screen.
     *
     * @param gr the Swing Graphics context
     */
    public void paint(final Graphics gr) {

        if (gotFontDimensions == false) {
            // Lazy-load the text width/height
            getFontDimensions(gr);
            /*
            System.err.println("textWidth " + textWidth +
                " textHeight " + textHeight);
            System.err.println("FONT: " + swing.getFont() + " font " + font);
             */
        }

        if ((swing.getFrame() != null)
            && (swing.getBufferStrategy() != null)
            && (SwingUtilities.isEventDispatchThread())
        ) {
            // System.err.println("paint(), skip first paint on swing thread");
            return;
        }

        int xCellMin = 0;
        int xCellMax = width;
        int yCellMin = 0;
        int yCellMax = height;

        Rectangle bounds = gr.getClipBounds();
        if (bounds != null) {
            // Only update what is in the bounds
            xCellMin = textColumn(bounds.x);
            xCellMax = textColumn(bounds.x + bounds.width);
            if (xCellMax > width) {
                xCellMax = width;
            }
            if (xCellMin >= xCellMax) {
                xCellMin = xCellMax - 2;
            }
            if (xCellMin < 0) {
                xCellMin = 0;
            }
            yCellMin = textRow(bounds.y);
            yCellMax = textRow(bounds.y + bounds.height);
            if (yCellMax > height) {
                yCellMax = height;
            }
            if (yCellMin >= yCellMax) {
                yCellMin = yCellMax - 2;
            }
            if (yCellMin < 0) {
                yCellMin = 0;
            }
        } else {
            // We need a total repaint
            reallyCleared = true;
        }

        // Prevent updates to the screen's data from the TApplication
        // threads.
        synchronized (this) {

            /*
            System.err.printf("bounds %s X %d %d Y %d %d\n",
                 bounds, xCellMin, xCellMax, yCellMin, yCellMax);
            */

            for (int y = yCellMin; y < yCellMax; y++) {
                for (int x = xCellMin; x < xCellMax; x++) {

                    int xPixel = x * textWidth + left;
                    int yPixel = y * textHeight + top;

                    Cell lCell = logical[x][y];
                    Cell pCell = physical[x][y];

                    if (!lCell.equals(pCell)
                        || lCell.isBlink()
                        || reallyCleared
                        || (swing.getFrame() == null)) {

                        drawGlyph(gr, lCell, xPixel, yPixel);

                        // Physical is always updated
                        physical[x][y].setTo(lCell);
                    }
                }
            }
            drawCursor(gr);

            reallyCleared = false;
        } // synchronized (this)
    }

    /**
     * Restore terminal to normal state.
     */
    public void shutdown() {
        swing.dispose();
    }

    /**
     * Push the logical screen to the physical device.
     */
    private void drawToSwing() {

        /*
        System.err.printf("drawToSwing(): reallyCleared %s dirty %s\n",
            reallyCleared, dirty);
        */

        // If reallyCleared is set, we have to draw everything.
        if ((swing.getFrame() != null)
            && (swing.getBufferStrategy() != null)
            && (reallyCleared == true)
        ) {
            // Triple-buffering: we have to redraw everything on this thread.
            Graphics gr = swing.getBufferStrategy().getDrawGraphics();
            swing.paint(gr);
            gr.dispose();
            swing.getBufferStrategy().show();
            Toolkit.getDefaultToolkit().sync();
            return;
        } else if (((swing.getFrame() != null)
                && (swing.getBufferStrategy() == null))
            || (reallyCleared == true)
        ) {
            // Repaint everything on the Swing thread.
            // System.err.println("REPAINT ALL");
            swing.repaint();
            return;
        }

        if ((swing.getFrame() != null) && (swing.getBufferStrategy() != null)) {
            Graphics gr = swing.getBufferStrategy().getDrawGraphics();

            synchronized (this) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        Cell lCell = logical[x][y];
                        Cell pCell = physical[x][y];

                        int xPixel = x * textWidth + left;
                        int yPixel = y * textHeight + top;

                        if (!lCell.equals(pCell)
                            || ((x == cursorX)
                                && (y == cursorY)
                                && cursorVisible)
                            || (lCell.isBlink())
                        ) {
                            drawGlyph(gr, lCell, xPixel, yPixel);
                            physical[x][y].setTo(lCell);
                        }
                    }
                }
                drawCursor(gr);
            } // synchronized (this)

            gr.dispose();
            swing.getBufferStrategy().show();
            Toolkit.getDefaultToolkit().sync();
            return;
        }

        // Swing thread version: request a repaint, but limit it to the area
        // that has changed.

        // Find the minimum-size damaged region.
        int xMin = swing.getWidth();
        int xMax = 0;
        int yMin = swing.getHeight();
        int yMax = 0;

        synchronized (this) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    Cell lCell = logical[x][y];
                    Cell pCell = physical[x][y];

                    int xPixel = x * textWidth + left;
                    int yPixel = y * textHeight + top;

                    if (!lCell.equals(pCell)
                        || ((x == cursorX)
                            && (y == cursorY)
                            && cursorVisible)
                        || lCell.isBlink()
                    ) {
                        if (xPixel < xMin) {
                            xMin = xPixel;
                        }
                        if (xPixel + textWidth > xMax) {
                            xMax = xPixel + textWidth;
                        }
                        if (yPixel < yMin) {
                            yMin = yPixel;
                        }
                        if (yPixel + textHeight > yMax) {
                            yMax = yPixel + textHeight;
                        }
                    }
                }
            }
        }
        if (xMin + textWidth >= xMax) {
            xMax += textWidth;
        }
        if (yMin + textHeight >= yMax) {
            yMax += textHeight;
        }

        // Repaint the desired area
        /*
        System.err.printf("REPAINT X %d %d Y %d %d\n", xMin, xMax,
            yMin, yMax);
        */

        if ((swing.getFrame() != null) && (swing.getBufferStrategy() != null)) {
            // This path should never be taken, but is left here for
            // completeness.
            Graphics gr = swing.getBufferStrategy().getDrawGraphics();
            Rectangle bounds = new Rectangle(xMin, yMin, xMax - xMin,
                yMax - yMin);
            gr.setClip(bounds);
            swing.paint(gr);
            gr.dispose();
            swing.getBufferStrategy().show();
            Toolkit.getDefaultToolkit().sync();
        } else {
            // Repaint on the Swing thread.
            swing.repaint(xMin, yMin, xMax - xMin, yMax - yMin);
        }
    }

    /**
     * Convert pixel column position to text cell column position.
     *
     * @param x pixel column position
     * @return text cell column position
     */
    public int textColumn(final int x) {
        int column = ((x - left) / textWidth);
        if (column < 0) {
            column = 0;
        }
        if (column > width - 1) {
            column = width - 1;
        }
        return column;
    }

    /**
     * Convert pixel row position to text cell row position.
     *
     * @param y pixel row position
     * @return text cell row position
     */
    public int textRow(final int y) {
        int row = ((y - top) / textHeight);
        if (row < 0) {
            row = 0;
        }
        if (row > height - 1) {
            row = height - 1;
        }
        return row;
    }

    /**
     * Getter for sessionInfo.
     *
     * @return the SessionInfo
     */
    public SessionInfo getSessionInfo() {
        return sessionInfo;
    }

    /**
     * Getter for the underlying Swing component.
     *
     * @return the SwingComponent
     */
    public SwingComponent getSwingComponent() {
        return swing;
    }

    // ------------------------------------------------------------------------
    // KeyListener ------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Pass Swing keystrokes into the event queue.
     *
     * @param key keystroke received
     */
    public void keyReleased(final KeyEvent key) {
        // Ignore release events
    }

    /**
     * Pass Swing keystrokes into the event queue.
     *
     * @param key keystroke received
     */
    public void keyTyped(final KeyEvent key) {
        // Ignore typed events
    }

    /**
     * Pass Swing keystrokes into the event queue.
     *
     * @param key keystroke received
     */
    public void keyPressed(final KeyEvent key) {
        boolean alt = false;
        boolean shift = false;
        boolean ctrl = false;
        char ch = ' ';
        boolean isKey = false;
        if (key.isActionKey()) {
            isKey = true;
        } else {
            ch = key.getKeyChar();
        }
        alt = key.isAltDown();
        ctrl = key.isControlDown();
        shift = key.isShiftDown();

        /*
        System.err.printf("Swing Key: %s\n", key);
        System.err.printf("   isKey: %s\n", isKey);
        System.err.printf("   alt: %s\n", alt);
        System.err.printf("   ctrl: %s\n", ctrl);
        System.err.printf("   shift: %s\n", shift);
        System.err.printf("   ch: %s\n", ch);
        */

        // Special case: not return the bare modifier presses
        switch (key.getKeyCode()) {
        case KeyEvent.VK_ALT:
            return;
        case KeyEvent.VK_ALT_GRAPH:
            return;
        case KeyEvent.VK_CONTROL:
            return;
        case KeyEvent.VK_SHIFT:
            return;
        case KeyEvent.VK_META:
            return;
        default:
            break;
        }

        TKeypress keypress = null;
        if (isKey) {
            switch (key.getKeyCode()) {
            case KeyEvent.VK_F1:
                keypress = new TKeypress(true, TKeypress.F1, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_F2:
                keypress = new TKeypress(true, TKeypress.F2, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_F3:
                keypress = new TKeypress(true, TKeypress.F3, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_F4:
                keypress = new TKeypress(true, TKeypress.F4, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_F5:
                keypress = new TKeypress(true, TKeypress.F5, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_F6:
                keypress = new TKeypress(true, TKeypress.F6, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_F7:
                keypress = new TKeypress(true, TKeypress.F7, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_F8:
                keypress = new TKeypress(true, TKeypress.F8, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_F9:
                keypress = new TKeypress(true, TKeypress.F9, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_F10:
                keypress = new TKeypress(true, TKeypress.F10, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_F11:
                keypress = new TKeypress(true, TKeypress.F11, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_F12:
                keypress = new TKeypress(true, TKeypress.F12, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_HOME:
                keypress = new TKeypress(true, TKeypress.HOME, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_END:
                keypress = new TKeypress(true, TKeypress.END, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_PAGE_UP:
                keypress = new TKeypress(true, TKeypress.PGUP, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_PAGE_DOWN:
                keypress = new TKeypress(true, TKeypress.PGDN, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_INSERT:
                keypress = new TKeypress(true, TKeypress.INS, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_DELETE:
                keypress = new TKeypress(true, TKeypress.DEL, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_RIGHT:
                keypress = new TKeypress(true, TKeypress.RIGHT, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_LEFT:
                keypress = new TKeypress(true, TKeypress.LEFT, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_UP:
                keypress = new TKeypress(true, TKeypress.UP, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_DOWN:
                keypress = new TKeypress(true, TKeypress.DOWN, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_TAB:
                // Special case: distinguish TAB vs BTAB
                if (shift) {
                    keypress = kbShiftTab;
                } else {
                    keypress = kbTab;
                }
                break;
            case KeyEvent.VK_ENTER:
                keypress = new TKeypress(true, TKeypress.ENTER, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_ESCAPE:
                keypress = new TKeypress(true, TKeypress.ESC, ' ',
                    alt, ctrl, shift);
                break;
            case KeyEvent.VK_BACK_SPACE:
                keypress = kbBackspace;
                break;
            default:
                // Unsupported, ignore
                return;
            }
        }

        if (keypress == null) {
            switch (ch) {
            case 0x08:
                // Disambiguate ^H from Backspace.
                if (KeyEvent.getKeyText(key.getKeyCode()).equals("H")) {
                    // This is ^H.
                    keypress = kbBackspace;
                } else {
                    // We are emulating Xterm here, where the backspace key
                    // on the keyboard returns ^?.
                    keypress = kbBackspaceDel;
                }
                break;
            case 0x0A:
                keypress = kbEnter;
                break;
            case 0x1B:
                keypress = kbEsc;
                break;
            case 0x0D:
                keypress = kbEnter;
                break;
            case 0x09:
                if (shift) {
                    keypress = kbShiftTab;
                } else {
                    keypress = kbTab;
                }
                break;
            case 0x7F:
                keypress = kbDel;
                break;
            default:
                if (!alt && ctrl && !shift) {
                    ch = KeyEvent.getKeyText(key.getKeyCode()).charAt(0);
                }
                // Not a special key, put it together
                keypress = new TKeypress(false, 0, ch, alt, ctrl, shift);
            }
        }

        // Save it and we are done.
        synchronized (eventQueue) {
            eventQueue.add(new TKeypressEvent(keypress));
            resetBlinkTimer();
        }
        if (listener != null) {
            synchronized (listener) {
                listener.notifyAll();
            }
        }
    }

    // ------------------------------------------------------------------------
    // WindowListener ---------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Pass window events into the event queue.
     *
     * @param event window event received
     */
    public void windowActivated(final WindowEvent event) {
        // Force a total repaint
        synchronized (this) {
            clearPhysical();
        }
    }

    /**
     * Pass window events into the event queue.
     *
     * @param event window event received
     */
    public void windowClosed(final WindowEvent event) {
        // Ignore
    }

    /**
     * Pass window events into the event queue.
     *
     * @param event window event received
     */
    public void windowClosing(final WindowEvent event) {
        // Drop a cmAbort and walk away
        synchronized (eventQueue) {
            eventQueue.add(new TCommandEvent(cmAbort));
            resetBlinkTimer();
        }
        if (listener != null) {
            synchronized (listener) {
                listener.notifyAll();
            }
        }
    }

    /**
     * Pass window events into the event queue.
     *
     * @param event window event received
     */
    public void windowDeactivated(final WindowEvent event) {
        // Ignore
    }

    /**
     * Pass window events into the event queue.
     *
     * @param event window event received
     */
    public void windowDeiconified(final WindowEvent event) {
        // Ignore
    }

    /**
     * Pass window events into the event queue.
     *
     * @param event window event received
     */
    public void windowIconified(final WindowEvent event) {
        // Ignore
    }

    /**
     * Pass window events into the event queue.
     *
     * @param event window event received
     */
    public void windowOpened(final WindowEvent event) {
        // Ignore
    }

    // ------------------------------------------------------------------------
    // ComponentListener ------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Pass component events into the event queue.
     *
     * @param event component event received
     */
    public void componentHidden(final ComponentEvent event) {
        // Ignore
    }

    /**
     * Pass component events into the event queue.
     *
     * @param event component event received
     */
    public void componentShown(final ComponentEvent event) {
        // Ignore
    }

    /**
     * Pass component events into the event queue.
     *
     * @param event component event received
     */
    public void componentMoved(final ComponentEvent event) {
        // Ignore
    }

    /**
     * Pass component events into the event queue.
     *
     * @param event component event received
     */
    public void componentResized(final ComponentEvent event) {
        if (gotFontDimensions == false) {
            // We are still waiting to get font information.  Don't pass a
            // resize event up.
            // System.err.println("size " + swing.getComponent().getSize());
            return;
        }

        // Drop a new TResizeEvent into the queue
        sessionInfo.queryWindowSize();
        synchronized (eventQueue) {
            TResizeEvent windowResize = new TResizeEvent(TResizeEvent.Type.SCREEN,
                sessionInfo.getWindowWidth(), sessionInfo.getWindowHeight());
            eventQueue.add(windowResize);
            resetBlinkTimer();
            /*
            System.err.println("Add resize event: " + windowResize.getWidth() +
                " x " + windowResize.getHeight());
             */
        }
        if (listener != null) {
            synchronized (listener) {
                listener.notifyAll();
            }
        }
    }

    // ------------------------------------------------------------------------
    // MouseMotionListener ----------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Pass mouse events into the event queue.
     *
     * @param mouse mouse event received
     */
    public void mouseDragged(final MouseEvent mouse) {
        int modifiers = mouse.getModifiersEx();
        boolean eventMouse1 = false;
        boolean eventMouse2 = false;
        boolean eventMouse3 = false;
        if ((modifiers & MouseEvent.BUTTON1_DOWN_MASK) != 0) {
            eventMouse1 = true;
        }
        if ((modifiers & MouseEvent.BUTTON2_DOWN_MASK) != 0) {
            eventMouse2 = true;
        }
        if ((modifiers & MouseEvent.BUTTON3_DOWN_MASK) != 0) {
            eventMouse3 = true;
        }
        mouse1 = eventMouse1;
        mouse2 = eventMouse2;
        mouse3 = eventMouse3;
        int x = textColumn(mouse.getX());
        int y = textRow(mouse.getY());

        TMouseEvent mouseEvent = new TMouseEvent(TMouseEvent.Type.MOUSE_MOTION,
            x, y, x, y, mouse1, mouse2, mouse3, false, false);

        synchronized (eventQueue) {
            eventQueue.add(mouseEvent);
            resetBlinkTimer();
        }
        if (listener != null) {
            synchronized (listener) {
                listener.notifyAll();
            }
        }
    }

    /**
     * Pass mouse events into the event queue.
     *
     * @param mouse mouse event received
     */
    public void mouseMoved(final MouseEvent mouse) {
        int x = textColumn(mouse.getX());
        int y = textRow(mouse.getY());
        if ((x == oldMouseX) && (y == oldMouseY)) {
            // Bail out, we've moved some pixels but not a whole text cell.
            return;
        }
        oldMouseX = x;
        oldMouseY = y;

        TMouseEvent mouseEvent = new TMouseEvent(TMouseEvent.Type.MOUSE_MOTION,
            x, y, x, y, mouse1, mouse2, mouse3, false, false);

        synchronized (eventQueue) {
            eventQueue.add(mouseEvent);
            resetBlinkTimer();
        }
        if (listener != null) {
            synchronized (listener) {
                listener.notifyAll();
            }
        }
    }

    // ------------------------------------------------------------------------
    // MouseListener ----------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Pass mouse events into the event queue.
     *
     * @param mouse mouse event received
     */
    public void mouseClicked(final MouseEvent mouse) {
        // Ignore
    }

    /**
     * Pass mouse events into the event queue.
     *
     * @param mouse mouse event received
     */
    public void mouseEntered(final MouseEvent mouse) {
        // Ignore
    }

    /**
     * Pass mouse events into the event queue.
     *
     * @param mouse mouse event received
     */
    public void mouseExited(final MouseEvent mouse) {
        // Ignore
    }

    /**
     * Pass mouse events into the event queue.
     *
     * @param mouse mouse event received
     */
    public void mousePressed(final MouseEvent mouse) {
        int modifiers = mouse.getModifiersEx();
        boolean eventMouse1 = false;
        boolean eventMouse2 = false;
        boolean eventMouse3 = false;
        if ((modifiers & MouseEvent.BUTTON1_DOWN_MASK) != 0) {
            eventMouse1 = true;
        }
        if ((modifiers & MouseEvent.BUTTON2_DOWN_MASK) != 0) {
            eventMouse2 = true;
        }
        if ((modifiers & MouseEvent.BUTTON3_DOWN_MASK) != 0) {
            eventMouse3 = true;
        }
        mouse1 = eventMouse1;
        mouse2 = eventMouse2;
        mouse3 = eventMouse3;
        int x = textColumn(mouse.getX());
        int y = textRow(mouse.getY());

        TMouseEvent mouseEvent = new TMouseEvent(TMouseEvent.Type.MOUSE_DOWN,
            x, y, x, y, mouse1, mouse2, mouse3, false, false);

        synchronized (eventQueue) {
            eventQueue.add(mouseEvent);
            resetBlinkTimer();
        }
        if (listener != null) {
            synchronized (listener) {
                listener.notifyAll();
            }
        }
    }

    /**
     * Pass mouse events into the event queue.
     *
     * @param mouse mouse event received
     */
    public void mouseReleased(final MouseEvent mouse) {
        int modifiers = mouse.getModifiersEx();
        boolean eventMouse1 = false;
        boolean eventMouse2 = false;
        boolean eventMouse3 = false;
        if ((modifiers & MouseEvent.BUTTON1_DOWN_MASK) != 0) {
            eventMouse1 = true;
        }
        if ((modifiers & MouseEvent.BUTTON2_DOWN_MASK) != 0) {
            eventMouse2 = true;
        }
        if ((modifiers & MouseEvent.BUTTON3_DOWN_MASK) != 0) {
            eventMouse3 = true;
        }
        if (mouse1) {
            mouse1 = false;
            eventMouse1 = true;
        }
        if (mouse2) {
            mouse2 = false;
            eventMouse2 = true;
        }
        if (mouse3) {
            mouse3 = false;
            eventMouse3 = true;
        }
        int x = textColumn(mouse.getX());
        int y = textRow(mouse.getY());

        TMouseEvent mouseEvent = new TMouseEvent(TMouseEvent.Type.MOUSE_UP,
            x, y, x, y, eventMouse1, eventMouse2, eventMouse3, false, false);

        synchronized (eventQueue) {
            eventQueue.add(mouseEvent);
            resetBlinkTimer();
        }
        if (listener != null) {
            synchronized (listener) {
                listener.notifyAll();
            }
        }
    }

    // ------------------------------------------------------------------------
    // MouseWheelListener -----------------------------------------------------
    // ------------------------------------------------------------------------

    /**
     * Pass mouse events into the event queue.
     *
     * @param mouse mouse event received
     */
    public void mouseWheelMoved(final MouseWheelEvent mouse) {
        int modifiers = mouse.getModifiersEx();
        boolean eventMouse1 = false;
        boolean eventMouse2 = false;
        boolean eventMouse3 = false;
        boolean mouseWheelUp = false;
        boolean mouseWheelDown = false;
        if ((modifiers & MouseEvent.BUTTON1_DOWN_MASK) != 0) {
            eventMouse1 = true;
        }
        if ((modifiers & MouseEvent.BUTTON2_DOWN_MASK) != 0) {
            eventMouse2 = true;
        }
        if ((modifiers & MouseEvent.BUTTON3_DOWN_MASK) != 0) {
            eventMouse3 = true;
        }
        mouse1 = eventMouse1;
        mouse2 = eventMouse2;
        mouse3 = eventMouse3;
        int x = textColumn(mouse.getX());
        int y = textRow(mouse.getY());
        if (mouse.getWheelRotation() > 0) {
            mouseWheelDown = true;
        }
        if (mouse.getWheelRotation() < 0) {
            mouseWheelUp = true;
        }

        TMouseEvent mouseEvent = new TMouseEvent(TMouseEvent.Type.MOUSE_DOWN,
            x, y, x, y, mouse1, mouse2, mouse3, mouseWheelUp, mouseWheelDown);

        synchronized (eventQueue) {
            eventQueue.add(mouseEvent);
            resetBlinkTimer();
        }
        if (listener != null) {
            synchronized (listener) {
                listener.notifyAll();
            }
        }
    }

}
