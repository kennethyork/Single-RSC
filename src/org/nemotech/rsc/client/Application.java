package org.nemotech.rsc.client;

import org.nemotech.rsc.Constants;

import java.awt.*;
import java.awt.event.*;

public class Application extends Frame {

    private Shell shell;

    public Application(Shell shell) {
        this.shell = shell;
        setTitle(Constants.APPLICATION_TITLE);
        setBackground(Color.BLACK);
        setResizable(true);
        pack();
        toFront();
        setVisible(true);
        setSize(Constants.APPLICATION_WIDTH, Constants.APPLICATION_HEIGHT);
        setMinimumSize(new Dimension(512, 344));
        setMaximumSize(new Dimension(1535, 1800));
        setLocationRelativeTo(null); // center the frame
        addListeners();
    }
    
    private void addListeners() {
        addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                int rotation = e.getWheelRotation();
                shell.handleMouseScroll(rotation);
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shell.closeProgram();
            }
        });
        if (Constants.APPLICATION_RESIZABLE) {
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    // Compute the actual content area dimensions (subtract window insets)
                    Insets insets = getInsets();
                    int width = getWidth() - insets.left - insets.right;
                    int height = getHeight() - insets.top - insets.bottom;
                    shell.doResize(width, height);
                }
            });
        }
    }

    @Override
    public Graphics getGraphics() {
        Graphics graphics = super.getGraphics();
        // Translate graphics context to the top-left of the content area
        Insets insets = getInsets();
        graphics.translate(insets.left, insets.top);
        return graphics;
    }

    @Override
    public void setSize(int width, int height) {
        Insets insets = getInsets();
        if (insets != null) {
            super.setSize(
                width + insets.left + insets.right,
                height + insets.top + insets.bottom
            );
        } else {
            super.setSize(width, height);
        }
    }

    @Override
    protected void processEvent(AWTEvent e) {
        if (e instanceof MouseEvent) {
            if(e instanceof MouseWheelEvent) {
                MouseWheelEvent evt = (MouseWheelEvent) e;
                e = new MouseWheelEvent(evt.getComponent(), evt.getID(), evt.getWhen(), evt.getModifiers(), evt.getX(), evt.getY() - 24, evt.getClickCount(), evt.isPopupTrigger(), evt.getScrollType(), evt.getScrollAmount(), evt.getWheelRotation());
            } else {
                MouseEvent evt = (MouseEvent) e;
                e = new MouseEvent(evt.getComponent(), evt.getID(), evt.getWhen(), evt.getModifiers(), evt.getX(), evt.getY() - 24, evt.getClickCount(), evt.isPopupTrigger());
            }
        }
        super.processEvent(e);
    }

    @Override
    public void paint(Graphics g) {
        shell.paint(g);
    }
    
}
