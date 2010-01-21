/*
 * $Id$
 * IzPack - Copyright 2001-2008 Julien Ponge, All Rights Reserved.
 *
 * http://izpack.org/
 * http://izpack.codehaus.org/
 *
 * Copyright 2002 Jan Blok
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.izforge.izpack.installer.base;

import com.izforge.izpack.api.adaptator.IXMLElement;
import com.izforge.izpack.api.adaptator.IXMLWriter;
import com.izforge.izpack.api.adaptator.impl.XMLWriter;
import com.izforge.izpack.data.Info;
import com.izforge.izpack.data.LocaleDatabase;
import com.izforge.izpack.data.Panel;
import com.izforge.izpack.data.ResourceManager;
import com.izforge.izpack.gui.ButtonFactory;
import com.izforge.izpack.gui.EtchedLineBorder;
import com.izforge.izpack.gui.IconsDatabase;
import com.izforge.izpack.installer.GUIListener;
import com.izforge.izpack.installer.ResourceNotFoundException;
import com.izforge.izpack.installer.UninstallData;
import com.izforge.izpack.installer.data.GUIInstallData;
import com.izforge.izpack.installer.data.UninstallDataWriter;
import com.izforge.izpack.installer.debugger.Debugger;
import com.izforge.izpack.installer.unpacker.IUnpacker;
import com.izforge.izpack.installer.unpacker.Unpacker;
import com.izforge.izpack.panels.PanelManager;
import com.izforge.izpack.rules.RulesEngine;
import com.izforge.izpack.util.*;
import com.izforge.izpack.util.substitutor.VariableSubstitutor;
import com.izforge.izpack.util.substitutor.VariableSubstitutorImpl;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.izforge.izpack.installer.base.GuiId.*;

/**
 * The IzPack installer frame.
 *
 * @author Julien Ponge created October 27, 2002
 * @author Fabrice Mirabile added fix for alert window on cross button, July 06 2005
 * @author Dennis Reil, added RulesEngine November 10 2006, several changes in January 2007
 */
public class InstallerFrame extends JFrame {

    private static final long serialVersionUID = 3257852069162727473L;

    /**
     * VM version to use version dependent methods calls
     */
    private static final float JAVA_SPECIFICATION_VERSION = Float.parseFloat(System
            .getProperty("java.specification.version"));

    private static final String ICON_RESOURCE = "Installer.image";

    /**
     * Name of the variable where to find an extension to the resource name of the icon resource
     */
    private static final String ICON_RESOURCE_EXT_VARIABLE_NAME = "installerimage.ext";

    /**
     * Heading icon resource name.
     */
    private static final String HEADING_ICON_RESOURCE = "Heading.image";

    /**
     * The language pack.
     */
    public LocaleDatabase langpack;

    /**
     * The installation data.
     */
    protected GUIInstallData installdata;

    /**
     * The icons database.
     */
    public IconsDatabase icons;

    /**
     * The panels container.
     */
    protected JPanel panelsContainer;

    /**
     * The frame content pane.
     */
    protected JPanel contentPane;

    /**
     * The help button.
     */
    protected JButton helpButton = null;

    /**
     * The previous button.
     */
    protected JButton prevButton;

    /**
     * The next button.
     */
    protected JButton nextButton;

    /**
     * The quit button.
     */
    protected JButton quitButton;


    /**
     * Registered GUICreationListener.
     */
    protected ArrayList<GUIListener> guiListener;

    /**
     * Heading major text.
     */
    protected JLabel[] headingLabels;

    /**
     * Panel which contains the heading text and/or icon
     */
    protected JPanel headingPanel;

    /**
     * The heading counter component.
     */
    protected JComponent headingCounterComponent;

    /**
     * Image
     */
    private JLabel iconLabel;

    /**
     * Count for discarded interrupt trials.
     */
    private int interruptCount = 1;

    /**
     * Maximum of discarded interrupt trials.
     */
    private static final int MAX_INTERRUPT = 3;

    /**
     * conditions
     */
    protected RulesEngine rules;


    private VariableSubstitutor substitutor;

    private Debugger debugger;

    // If a heading image is defined should it be displayed on the left
    private boolean imageLeft = false;

    /**
     * Panel manager
     */
    private PanelManager panelManager;

    /**
     * The resource manager
     */
    private ResourceManager resourceManager;

    /**
     * Manager for writing uninstall data
     */
    private UninstallDataWriter uninstallDataWriter;
    private VariableSubstitutor variableSubstitutor;

    /**
     * The constructor (normal mode).
     *
     * @param variableSubstitutor
     * @param title               The window title.
     * @param installdata         The installation data.
     * @throws Exception Description of the Exception
     */
    public InstallerFrame(String title, GUIInstallData installdata, RulesEngine rules, IconsDatabase icons, PanelManager panelManager, UninstallDataWriter uninstallDataWriter, ResourceManager resourceManager, VariableSubstitutor variableSubstitutor)
            throws Exception {
        super(title);
        substitutor = variableSubstitutor;
        guiListener = new ArrayList<GUIListener>();
        this.installdata = installdata;
        this.langpack = installdata.getLangpack();
        this.rules = rules;
        this.icons = icons;
        this.resourceManager = resourceManager;
        this.uninstallDataWriter = uninstallDataWriter;
        this.panelManager = panelManager;
        // Sets the window events handler
        addWindowListener(new WindowHandler());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        this.variableSubstitutor = new VariableSubstitutorImpl(this.installdata.getVariables());
    }

    public void sizeFrame() {
        pack();
        setSize(installdata.guiPrefs.width, installdata.guiPrefs.height);
        setPreferredSize(new Dimension(installdata.guiPrefs.width, installdata.guiPrefs.height));
        setResizable(installdata.guiPrefs.resizable);
        centerFrame(this);
    }

    /**
     * Search and construct all necessary panels, prepare and size the frame.
     *
     * @return The frame for fluent interface
     * @throws ClassNotFoundException
     */
    public InstallerFrame loadPanels() throws ClassNotFoundException {
        panelManager.loadPanelsInContainer();
        panelManager.instanciatePanels();
        buildGUI();
        sizeFrame();
        return this;
    }

    public void enableFrame() {
        // We show the frame
        showFrame();
        switchPanel(0);
    }

    public Debugger getDebugger() {
        return this.debugger;
    }

    /**
     * Builds the GUI.
     */
    private void buildGUI() {
        this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); // patch 06/07/2005,
        // Fabrice Mirabile
        // Sets the frame icon
        setIconImage(icons.getImageIcon("JFrameIcon").getImage());

        // Prepares the glass pane to block the gui interaction when needed
        JPanel glassPane = (JPanel) getGlassPane();
        glassPane.addMouseListener(new MouseAdapter() {/* Nothing todo */
        });
        glassPane.addMouseMotionListener(new MouseMotionAdapter() {/* Nothing todo */
        });
        glassPane.addKeyListener(new KeyAdapter() {/* Nothing todo */
        });
        glassPane.addFocusListener(new FocusAdapter() {/* Nothing todo */
        });

        // We set the layout & prepare the constraint object
        contentPane = (JPanel) getContentPane();
        contentPane.setLayout(new BorderLayout()); // layout);

        // We add the panels container
        panelsContainer = new JPanel();
        panelsContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        panelsContainer.setLayout(new GridLayout(1, 1));
        contentPane.add(panelsContainer, BorderLayout.CENTER);

        // We put the first panel
        installdata.setCurPanelNumber(0);
        IzPanel panel_0 = (IzPanel) installdata.getPanels().get(0);
        panelsContainer.add(panel_0);

        // We add the navigation buttons & labels

        NavigationHandler navHandler = new NavigationHandler();

        JPanel navPanel = new JPanel();
        navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.X_AXIS));
        navPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(8, 8,
                8, 8), BorderFactory.createTitledBorder(new EtchedLineBorder(), langpack
                .getString("installer.madewith")
                + " ", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font(
                "Dialog", Font.PLAIN, 10))));

        // Add help Button to the navigation panel
        this.helpButton = ButtonFactory.createButton(langpack.getString("installer.help"), icons
                .getImageIcon("help"), installdata.buttonsHColor);
        navPanel.add(this.helpButton);
        this.helpButton.setName(BUTTON_HELP.id);
        this.helpButton.addActionListener(new HelpHandler());

        navPanel.add(Box.createHorizontalGlue());

        prevButton = ButtonFactory.createButton(langpack.getString("installer.prev"), icons
                .getImageIcon("stepback"), installdata.buttonsHColor);
        navPanel.add(prevButton);
        prevButton.addActionListener(navHandler);
        prevButton.setName(BUTTON_PREV.id);

        navPanel.add(Box.createRigidArea(new Dimension(5, 0)));

        nextButton = ButtonFactory.createButton(langpack.getString("installer.next"), icons
                .getImageIcon("stepforward"), installdata.buttonsHColor);
        navPanel.add(nextButton);
        nextButton.setName(BUTTON_NEXT.id);
        nextButton.addActionListener(navHandler);

        navPanel.add(Box.createRigidArea(new Dimension(5, 0)));

        quitButton = ButtonFactory.createButton(langpack.getString("installer.quit"), icons
                .getImageIcon("stop"), installdata.buttonsHColor);
        navPanel.add(quitButton);
        quitButton.setName(BUTTON_QUIT.id);
        quitButton.addActionListener(navHandler);
        contentPane.add(navPanel, BorderLayout.SOUTH);

        // always initialize debugger
        debugger = new Debugger(installdata, icons, rules);
        // this needed to fully initialize the debugger.
        JPanel debugpanel = debugger.getDebugPanel();

        // create a debug panel if TRACE is enabled
        if (Debug.isTRACE()) {
            if (installdata.guiPrefs.modifier.containsKey("showDebugWindow")
                    && Boolean.valueOf(installdata.guiPrefs.modifier.get("showDebugWindow"))) {
                JFrame debugframe = new JFrame("Debug information");
                debugframe.setContentPane(debugpanel);
                debugframe.setSize(new Dimension(400, 400));
                debugframe.setVisible(true);
            } else {
                debugpanel.setPreferredSize(new Dimension(200, 400));
                contentPane.add(debugpanel, BorderLayout.EAST);
            }
        }

        try {
            ImageIcon icon = loadIcon(ICON_RESOURCE, 0, true);
            if (icon != null) {
                JPanel imgPanel = new JPanel();
                imgPanel.setLayout(new BorderLayout());
                imgPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 0));
                iconLabel = new JLabel(icon);
                iconLabel.setBorder(BorderFactory.createLoweredBevelBorder());
                imgPanel.add(iconLabel, BorderLayout.NORTH);
                contentPane.add(imgPanel, BorderLayout.WEST);
            }
        }
        catch (Exception e) {
            // ignore
        }

        loadAndShowImage(0);
        getRootPane().setDefaultButton(nextButton);
        callGUIListener(GUIListener.GUI_BUILDED, navPanel);
        createHeading(navPanel);
    }

    private void callGUIListener(int what) {
        callGUIListener(what, null);
    }

    private void callGUIListener(int what, Object param) {
        Iterator<GUIListener> iter = guiListener.iterator();
        while (iter.hasNext()) {
            (iter.next()).guiActionPerformed(what, param);
        }
    }

    /**
     * Loads icon for given panel.
     *
     * @param resPrefix   resources prefix.
     * @param PanelNo     panel id.
     * @param tryBaseIcon should try to fallback to base icon?
     * @return icon image
     * @throws ResourceNotFoundException
     * @throws IOException
     */
    private ImageIcon loadIcon(String resPrefix, int PanelNo, boolean tryBaseIcon)
            throws ResourceNotFoundException, IOException {
        ImageIcon icon;
        String iconext = this.getIconResourceNameExtension();
        if (tryBaseIcon) {
            try {
                icon = resourceManager.getImageIconResource(resPrefix);
            }
            catch (Exception e) // This is not that clean ...
            {
                icon = resourceManager.getImageIconResource(resPrefix + "." + PanelNo + iconext);
            }
        } else {
            icon = resourceManager.getImageIconResource(resPrefix + "." + PanelNo + iconext);
        }
        return (icon);
    }

    /**
     * Loads icon for given panel id.
     *
     * @param resPrefix   resource prefix.
     * @param panelid     panel id.
     * @param tryBaseIcon should try to load base icon?
     * @return image icon
     * @throws ResourceNotFoundException
     * @throws IOException
     */
    private ImageIcon loadIcon(String resPrefix, String panelid, boolean tryBaseIcon)
            throws ResourceNotFoundException, IOException {
        ImageIcon icon = null;
        String iconext = this.getIconResourceNameExtension();
        if (tryBaseIcon) {
            try {
                icon = resourceManager.getImageIconResource(resPrefix);
            }
            catch (Exception e) // This is not that clean ...
            {
                icon = resourceManager.getImageIconResource(resPrefix + "." + panelid + iconext);
            }
        } else {
            icon = resourceManager.getImageIconResource(resPrefix + "." + panelid + iconext);
        }
        return (icon);
    }

    /**
     * Returns the current set extension to icon resource names. Can be used to change the static
     * installer image based on user input
     *
     * @return a resource extension or an empty string if the variable was not set.
     */
    private String getIconResourceNameExtension() {
        try {
            String iconext = this.installdata.getVariable(ICON_RESOURCE_EXT_VARIABLE_NAME);
            if (iconext == null) {
                iconext = "";
            } else {

                if ((iconext.length() > 0) && (iconext.charAt(0) != '.')) {
                    iconext = "." + iconext;
                }
            }
            iconext = iconext.trim();
            return iconext;
        }
        catch (Exception e) {
            // in case of error, return an empty string
            return "";
        }
    }

    private void loadAndShowImage(int panelNo) {
        loadAndShowImage(iconLabel, ICON_RESOURCE, panelNo);
    }

    private void loadAndShowImage(int panelNo, String panelid) {
        loadAndShowImage(iconLabel, ICON_RESOURCE, panelNo, panelid);
    }

    private void loadAndShowImage(JLabel iLabel, String resPrefix, int panelno, String panelid) {
        ImageIcon icon = null;
        try {
            icon = loadIcon(resPrefix, panelid, false);
        }
        catch (Exception e) {
            try {
                icon = loadIcon(resPrefix, panelno, false);
            }
            catch (Exception ex) {
                try {
                    icon = loadIcon(resPrefix, panelid, true);
                }
                catch (Exception e1) {
                    // ignore
                }
            }
        }
        if (icon != null) {
            iLabel.setVisible(false);
            iLabel.setIcon(icon);
            iLabel.setVisible(true);
        }
    }

    private void loadAndShowImage(JLabel iLabel, String resPrefix, int panelNo) {
        ImageIcon icon = null;
        try {
            icon = loadIcon(resPrefix, panelNo, false);
        }
        catch (Exception e) {
            try {
                icon = loadIcon(resPrefix, panelNo, true);
            }
            catch (Exception e1) {
                // ignore
            }
        }
        if (icon != null) {
            iLabel.setVisible(false);
            iLabel.setIcon(icon);
            iLabel.setVisible(true);
        }
    }

    /**
     * Shows the frame.
     */
    private void showFrame() {
        setVisible(true);
    }

    /**
     * Here is persisted the direction of panel traversing.
     */
    private boolean isBack = false;

    /**
     * Switches the current panel.
     *
     * @param last Description of the Parameter
     */
    protected void switchPanel(int last) {
        // refresh dynamic variables every time, a panel switch is done
        installdata.refreshDynamicVariables();
        try {
            if (installdata.getCurPanelNumber() < last) {
                isBack = true;
            }
            panelsContainer.setVisible(false);
            IzPanel panel = (IzPanel) installdata.getPanels().get(installdata.getCurPanelNumber());
            IzPanel l_panel = (IzPanel) installdata.getPanels().get(last);
            showHelpButton(panel.canShowHelp());
            if (Debug.isTRACE()) {
                debugger.switchPanel(panel.getMetadata(), l_panel.getMetadata());
            }
            Log.getInstance().addDebugMessage(
                    "InstallerFrame.switchPanel: try switching panel from {0} to {1} ({2} to {3})",
                    new String[]{l_panel.getClass().getName(), panel.getClass().getName(),
                            Integer.toString(last), Integer.toString(installdata.getCurPanelNumber())},
                    DebugConstants.PANEL_TRACE, null);

            // instead of writing data here which leads to duplicated entries in
            // auto-installation script (bug # 4551), let's make data only immediately before
            // writing out that script.
            // l_panel.makeXMLData(installdata.xmlData.getChildAtIndex(last));
            // No previos button in the first visible panel

            if (panelManager.isVisible(installdata.getCurPanelNumber())) {
                prevButton.setVisible(false);
                lockPrevButton();
                unlockNextButton(); // if we push the button back at the license
                // panel
            }
            // Only the exit button in the last panel.
            else if (panelManager.isLast(installdata.getCurPanelNumber())) {
                prevButton.setVisible(false);
                nextButton.setVisible(false);
                lockNextButton();
            } else {
                if (hasNavigatePrevious(installdata.getCurPanelNumber(), true) != -1) {
                    prevButton.setVisible(true);
                    unlockPrevButton();
                } else {
                    lockPrevButton();
                    prevButton.setVisible(false);
                }
                if (hasNavigateNext(installdata.getCurPanelNumber(), true) != -1) {
                    nextButton.setVisible(true);
                    unlockNextButton();
                } else {
                    lockNextButton();
                    nextButton.setVisible(false);
                }

            }
            // With VM version >= 1.5 setting default button one time will not work.
            // Therefore we set it every panel switch and that also later. But in
            // the moment it seems so that the quit button will not used as default button.
            // No idea why... (Klaus Bartz, 06.09.25)
            SwingUtilities.invokeLater(new Runnable() {

                public void run() {
                    JButton cdb = null;
                    String buttonName = "next";
                    if (nextButton.isEnabled()) {
                        cdb = nextButton;
                        quitButton.setDefaultCapable(false);
                        prevButton.setDefaultCapable(false);
                        nextButton.setDefaultCapable(true);
                    } else if (quitButton.isEnabled()) {
                        cdb = quitButton;
                        buttonName = "quit";
                        quitButton.setDefaultCapable(true);
                        prevButton.setDefaultCapable(false);
                        nextButton.setDefaultCapable(false);
                    }
                    getRootPane().setDefaultButton(cdb);
                    Log.getInstance().addDebugMessage(
                            "InstallerFrame.switchPanel: setting {0} as default button",
                            new String[]{buttonName}, DebugConstants.PANEL_TRACE, null);
                }
            });

            // Change panels container to the current one.
            panelsContainer.remove(l_panel);
            l_panel.panelDeactivate();
            panelsContainer.add(panel);

            if (panel.getInitialFocus() != null) { // Initial focus hint should be performed after current panel
                // was added to the panels container, else the focus hint will
                // be ignored.
                // Give a hint for the initial focus to the system.
                final Component inFoc = panel.getInitialFocus();
                if (JAVA_SPECIFICATION_VERSION < 1.35) {
                    inFoc.requestFocus();
                } else { // On java VM version >= 1.5 it works only if
                    // invoke later will be used.
                    SwingUtilities.invokeLater(new Runnable() {

                        public void run() {
                            inFoc.requestFocusInWindow();
                        }
                    });
                }
                /*
                 * On editable text components position the caret to the end of the cust existent
                 * text.
                 */
                if (inFoc instanceof JTextComponent) {
                    JTextComponent inText = (JTextComponent) inFoc;
                    if (inText.isEditable() && inText.getDocument() != null) {
                        inText.setCaretPosition(inText.getDocument().getLength());
                    }
                }
            }
            performHeading(panel);
            performHeadingCounter(panel);
            panel.executePreActivationActions();
            panel.panelActivate();
            panelsContainer.setVisible(true);
            Panel metadata = panel.getMetadata();
            if ((metadata != null) && (!"UNKNOWN".equals(metadata.getPanelid()))) {
                loadAndShowImage(panelManager.getPanelVisibilityNumber(installdata.getCurPanelNumber()), metadata
                        .getPanelid());
            } else {
                loadAndShowImage(panelManager.getPanelVisibilityNumber(installdata.getCurPanelNumber()));
            }
            isBack = false;
            callGUIListener(GUIListener.PANEL_SWITCHED);
            Log.getInstance().addDebugMessage("InstallerFrame.switchPanel: switched", null,
                    DebugConstants.PANEL_TRACE, null);
        }
        catch (Exception err) {
            err.printStackTrace();
        }
    }

    /**
     * REFACTOR : A remplacer par l'utilisation directe de
     * resourceManager.getInputStream()
     * Gets the stream to a resource.
     *
     * @param res The resource id.
     * @return The resource value, null if not found
     * @throws Exception
     */
    public InputStream getResource(String res) throws Exception {
        InputStream result;
        String basePath = "";

        try {
            basePath = resourceManager.getResourceBasePath();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        result = this.getClass().getResourceAsStream(basePath + res);

        if (result == null) {
            throw new ResourceNotFoundException("Warning: Resource not found: "
                    + res);
        }
        return result;
    }

    /**
     * Centers a window on screen.
     *
     * @param frame The window tp center.
     */
    public void centerFrame(Window frame) {
        Point center = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
        Dimension frameSize = frame.getSize();
        frame.setLocation(center.x - frameSize.width / 2, center.y - frameSize.height / 2 - 10);
    }

    /**
     * Returns the panels container size.
     *
     * @return The panels container size.
     */
    public Dimension getPanelsContainerSize() {
        return panelsContainer.getSize();
    }

    /**
     * Sets the parameters of a GridBagConstraints object.
     *
     * @param gbc The constraints object.
     * @param gx  The x coordinates.
     * @param gy  The y coordinates.
     * @param gw  The width.
     * @param wx  The x wheight.
     * @param wy  The y wheight.
     * @param gh  Description of the Parameter
     */
    public void buildConstraints(GridBagConstraints gbc, int gx, int gy, int gw, int gh, double wx,
                                 double wy) {
        gbc.gridx = gx;
        gbc.gridy = gy;
        gbc.gridwidth = gw;
        gbc.gridheight = gh;
        gbc.weightx = wx;
        gbc.weighty = wy;
    }

    /**
     * Makes a clean closing.
     */
    public void exit() {
        // FIXME !!! Reboot handling
        if (installdata.isCanClose()
                || ((!nextButton.isVisible() || !nextButton.isEnabled()) && (!prevButton
                .isVisible() || !prevButton.isEnabled()))) {
            // this does nothing if the uninstaller was not included
            uninstallDataWriter.write(installdata);

            boolean reboot = false;
            if (installdata.isRebootNecessary()) {
                String message, title;
                System.out.println("[ There are file operations pending after reboot ]");
                switch (installdata.getInfo().getRebootAction()) {
                    case Info.REBOOT_ACTION_ALWAYS:
                        reboot = true;
                        break;
                    case Info.REBOOT_ACTION_ASK:
                        message = variableSubstitutor.substitute(langpack.getString("installer.reboot.ask.message"));
                        title = variableSubstitutor.substitute(langpack.getString("installer.reboot.ask.title"));
                        int res = JOptionPane
                                .showConfirmDialog(this, message, title, JOptionPane.YES_NO_OPTION);
                        if (res == JOptionPane.YES_OPTION) {
                            reboot = true;
                        }
                        break;
                    case Info.REBOOT_ACTION_NOTICE:
                        message = variableSubstitutor.substitute(langpack.getString("installer.reboot.notice.message"));
                        title = variableSubstitutor.substitute(langpack.getString("installer.reboot.notice.title"));
                        JOptionPane.showConfirmDialog(this, message, title, JOptionPane.OK_OPTION);
                        break;
                }
                if (reboot)
                    System.out.println("[ Rebooting now automatically ]");
            }

            Housekeeper.getInstance().shutDown(0, reboot);
        } else {
            // The installation is not over
            if (Unpacker.isDiscardInterrupt() && interruptCount < MAX_INTERRUPT) { // But we should not interrupt.
                interruptCount++;
                return;
            }
            // Use a alternate message and title if defined.
            final String mkey = "installer.quit.reversemessage";
            final String tkey = "installer.quit.reversetitle";
            String message = langpack.getString(mkey);
            String title = langpack.getString(tkey);
            // message equal to key -> no alternate message defined.
            if (message.indexOf(mkey) > -1) {
                message = langpack.getString("installer.quit.message");
            }
            // title equal to key -> no alternate title defined.
            if (title.indexOf(tkey) > -1) {
                title = langpack.getString("installer.quit.title");
            }
            // Now replace variables in message or title.
            VariableSubstitutor vs = variableSubstitutor;
            message = vs.substitute(message);
            title = vs.substitute(title);
            int res = JOptionPane
                    .showConfirmDialog(this, message, title, JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) {
                wipeAborted();
                Housekeeper.getInstance().shutDown(0);
            }
        }
    }

    /**
     * Wipes the written files when you abort the installation.
     */
    protected void wipeAborted() {
        // We set interrupt to all running Unpacker and wait 40 sec for maximum.
        // If interrupt is discarded (return value false), return immediately:
        if (!Unpacker.interruptAll(40000)) {
            return;
        }

        // Wipe the files that had been installed
        UninstallData u = UninstallData.getInstance();
        for (String p : u.getInstalledFilesList()) {
            File f = new File(p);
            f.delete();
        }
    }

    /**
     * Launches the installation.
     *
     * @param listener The installation listener.
     */
    public void install(AbstractUIProgressHandler listener) {
        IUnpacker unpacker = panelManager.getUnpacker(listener);
//        IUnpacker unpacker = UnpackerFactory.getUnpacker(this.installdata.getInfo()
//                .getUnpackerClassName(), installdata, listener);
//        unpacker.setRules(rules);
        Thread unpackerthread = new Thread(unpacker, "IzPack - Unpacker thread");
        unpackerthread.start();
    }

    /**
     * Writes an XML tree.
     *
     * @param root The XML tree to write out.
     * @param out  The stream to write on.
     * @throws Exception Description of the Exception
     */
    public void writeXMLTree(IXMLElement root, OutputStream out) throws Exception {
        IXMLWriter writer = new XMLWriter(out);
        // fix bug# 4551
        // write.write(root);
        for (int i = 0; i < installdata.getPanels().size(); i++) {
            IzPanel panel = (IzPanel) installdata.getPanels().get(i);
            panel.makeXMLData(installdata.getXmlData().getChildAtIndex(i));
        }
        writer.write(root);

    }

    /**
     * Changes the quit button text. If <tt>text</tt> is null, the default quit text is used.
     *
     * @param text text to be used for changes
     */
    public void setQuitButtonText(String text) {
        String text1 = text;
        if (text1 == null) {
            text1 = langpack.getString("installer.quit");
        }
        quitButton.setText(text1);
    }

    /**
     * Sets a new icon into the quit button if icons should be used, else nothing will be done.
     *
     * @param iconName name of the icon to be used
     */
    public void setQuitButtonIcon(String iconName) {
        String useButtonIcons = installdata.guiPrefs.modifier.get("useButtonIcons");

        if (useButtonIcons == null || "yes".equalsIgnoreCase(useButtonIcons)) {
            quitButton.setIcon(icons.getImageIcon(iconName));
        }
    }

    /**
     * FocusTraversalPolicy objects to handle keybord blocking; the declaration os Object allows to
     * use a pre version 1.4 VM.
     */
    private Object usualFTP = null;

    private Object blockFTP = null;

    /**
     * Blocks GUI interaction.
     */
    public void blockGUI() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        getGlassPane().setVisible(true);
        getGlassPane().setEnabled(true);
        // No traversal handling before VM version 1.4
        if (JAVA_SPECIFICATION_VERSION < 1.35) {
            return;
        }
        if (usualFTP == null) {
            usualFTP = getFocusTraversalPolicy();
        }
        if (blockFTP == null) {
            blockFTP = new BlockFocusTraversalPolicy();
        }
        setFocusTraversalPolicy((java.awt.FocusTraversalPolicy) blockFTP);
        getGlassPane().requestFocus();
        callGUIListener(GUIListener.GUI_BLOCKED);

    }

    /**
     * Releases GUI interaction.
     */
    public void releaseGUI() {
        getGlassPane().setEnabled(false);
        getGlassPane().setVisible(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        // No traversal handling before VM version 1.4
        if (JAVA_SPECIFICATION_VERSION < 1.35) {
            return;
        }
        setFocusTraversalPolicy((java.awt.FocusTraversalPolicy) usualFTP);
        callGUIListener(GUIListener.GUI_RELEASED);
    }

    /**
     * Locks the 'previous' button.
     */
    public void lockPrevButton() {
        prevButton.setEnabled(false);
    }

    /**
     * Locks the 'next' button.
     */
    public void lockNextButton() {
        nextButton.setEnabled(false);
    }

    /**
     * Unlocks the 'previous' button.
     */
    public void unlockPrevButton() {
        prevButton.setEnabled(true);
    }

    /**
     * Unlocks the 'next' button.
     */
    public void unlockNextButton() {
        unlockNextButton(true);
    }

    /**
     * Unlocks the 'next' button.
     *
     * @param requestFocus if <code>true</code> focus goes to <code>nextButton</code>
     */
    public void unlockNextButton(boolean requestFocus) {
        nextButton.setEnabled(true);
        if (requestFocus) {
            nextButton.requestFocusInWindow();
            getRootPane().setDefaultButton(nextButton);
            if (this.getFocusOwner() != null) {
                Debug.trace("Current focus owner: " + this.getFocusOwner().getName());
            }
            if (!(getRootPane().getDefaultButton() == nextButton)) {
                Debug.trace("Next button not default button, setting...");
                quitButton.setDefaultCapable(false);
                prevButton.setDefaultCapable(false);
                nextButton.setDefaultCapable(true);
                getRootPane().setDefaultButton(nextButton);
            }
        }
    }

    /**
     * Allows a panel to ask to be skipped.
     */
    public void skipPanel() {
        if (installdata.getCurPanelNumber() < installdata.getPanels().size() - 1) {
            if (isBack) {
                navigatePrevious(installdata.getCurPanelNumber());
            } else {
                navigateNext(installdata.getCurPanelNumber(), false);
            }
        }
    }

    /**
     * Method checks whether conditions are met to show the given panel.
     *
     * @param panelnumber the panel number to check
     * @return true or false
     */
    public boolean canShow(int panelnumber) {
        IzPanel panel = (IzPanel) installdata.getPanels().get(panelnumber);
        Panel panelmetadata = panel.getMetadata();
        String panelid = panelmetadata.getPanelid();
        Debug.trace("Current Panel: " + panelid);

        if (panelmetadata.hasCondition()) {
            Debug.log("Checking panelcondition");
            return rules.isConditionTrue(panelmetadata.getCondition());
        } else {
            if (!rules.canShowPanel(panelid, this.installdata.getVariables())) {
                // skip panel, if conditions for panel aren't met
                Debug.log("Skip panel with panelid=" + panelid);
                // panel should be skipped, so we have to decrement panelnumber for skipping
                return false;
            } else {
                return true;
            }
        }
    }

    /**
     * This function moves to the next panel
     */
    public void navigateNext() {
        // If the button is inactive this indicates that we cannot move
        // so we don't do the move
        if (!nextButton.isEnabled()) {
            return;
        }
        this.navigateNext(installdata.getCurPanelNumber(), true);
    }

    /**
     * This function searches for the next available panel, the search begins from given panel+1
     *
     * @param startPanel   the starting panel number
     * @param doValidation whether to do panel validation
     */
    public void navigateNext(int startPanel, boolean doValidation) {
        if ((installdata.getCurPanelNumber() < installdata.getPanels().size() - 1)) {
            // We must trasfer all fields into the variables before
            // panelconditions try to resolve the rules based on unassigned vars.
            final IzPanel panel = (IzPanel) installdata.getPanels().get(startPanel);
            panel.executePreValidationActions();
            boolean isValid = doValidation ? panel.panelValidated() : true;
            panel.executePostValidationActions();

            // check if we can display the next panel or if there was an error during actions that
            // disables the next button
            if (!nextButton.isEnabled()) {
                return;
            }

            // if this is not here, validation will
            // occur mutilple times while skipping panels through the recursion
            if (!isValid) {
                return;
            }

            // We try to show the next panel that we can.
            int nextPanel = hasNavigateNext(startPanel, false);
            if (-1 != nextPanel) {
                installdata.setCurPanelNumber(nextPanel);
                switchPanel(startPanel);
            }
        }
    }


    /**
     * Check to see if there is another panel that can be navigated to next. This checks the
     * successive panels to see if at least one can be shown based on the conditions associated with
     * the panels.
     *
     * @param startPanel  The panel to check from
     * @param visibleOnly Only check the visible panels
     * @return The panel that we can navigate to next or -1 if there is no panel that we can
     *         navigate next to
     */
    public int hasNavigateNext(int startPanel, boolean visibleOnly) {
        // Assume that we cannot navigate to another panel
        int res = -1;
        // Start from the panel given and check each one until we find one
        // that we can navigate to or until there are no more panels
        for (int panel = startPanel + 1; res == -1 && panel < installdata.getPanels().size(); panel++) {
            // See if we can show this panel
            if (!visibleOnly || panelManager.isVisible(panel)) {
                if (canShow(panel)) {
                    res = panel;
                }
            }
        }
        // Return the result
        return res;
    }

    /**
     * Check to see if there is another panel that can be navigated to previous. This checks the
     * previous panels to see if at least one can be shown based on the conditions associated with
     * the panels.
     *
     * @param endingPanel The panel to check from
     * @return The panel that we can navigate to previous or -1 if there is no panel that we can
     *         navigate previous to
     */
    public int hasNavigatePrevious(int endingPanel, boolean visibleOnly) {
        // Assume that we cannot navigate to another panel
        int res = -1;
        // Start from the panel given and check each one until we find one
        // that we can navigate to or until there are no more panels
        for (int panel = endingPanel - 1; res == -1 && panel >= 0; panel--) {
            // See if we can show this panel
            if (!visibleOnly || panelManager.isVisible(panel)) {
                if (canShow(panel)) {
                    res = panel;
                }
            }
        }
        // Return the result
        return res;
    }

    /**
     * This function moves to the previous panel
     */
    public void navigatePrevious() {
        // If the button is inactive this indicates that we cannot move
        // so we don't do the move
        if (!prevButton.isEnabled()) {
            return;
        }
        this.navigatePrevious(installdata.getCurPanelNumber());
    }

    /**
     * This function switches to the available panel that is just before the given one.
     *
     * @param endingPanel the panel to search backwards, beginning from this.
     */
    public void navigatePrevious(int endingPanel) {
        // We try to show the previous panel that we can.
        int prevPanel = hasNavigatePrevious(endingPanel, false);
        if (-1 != prevPanel) {
            installdata.setCurPanelNumber(prevPanel);
            switchPanel(endingPanel);
        }
    }

    /**
     * Show help Window
     */
    public void showHelp() {
        IzPanel izPanel = (IzPanel) installdata.getPanels().get(installdata.getCurPanelNumber());
        izPanel.showHelp();
    }

    public void launchGUI() {
        final InstallerFrame installerFrame = this;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    installerFrame.enableFrame();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

    }

    /**
     * Handles the events from the navigation bar elements.
     *
     * @author Julien Ponge
     */
    class NavigationHandler implements ActionListener {

        public void actionPerformed(final ActionEvent e) {
            /*
                Some panels activation may be slow, hence we
                block the GUI, spin a thread to handle navigation then
                release the GUI.
             */
            new Thread(new Runnable() {
                public void run() {

                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            blockGUI();
                        }
                    });

                    navigate(e);

                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            releaseGUI();
                        }
                    });
                }
            }).start();
        }

        private void navigate(ActionEvent e) {
            Object source = e.getSource();
            if (source == prevButton) {
                navigatePrevious();
            } else if (source == nextButton) {
                navigateNext();
            } else if (source == quitButton) {
                exit();
            }
        }
    }

    class HelpHandler implements ActionListener {

        /**
         * Actions handler.
         *
         * @param e The event.
         */
        public void actionPerformed(ActionEvent e) {
            showHelp();
        }
    }

    /**
     * The window events handler.
     *
     * @author julien created October 27, 2002
     */
    class WindowHandler extends WindowAdapter {

        /**
         * Window close is pressed,
         *
         * @param e The event.
         */
        public void windowClosing(WindowEvent e) {
            // We ask for confirmation
            exit();
        }

        /**
         * OLD VERSION We can't avoid the exit here, so don't call exit anywhere else.
         *
         * @param e The event.
         *
         * public void windowClosing(WindowEvent e) { if (Unpacker.isDiscardInterrupt() &&
         * interruptCount < MAX_INTERRUPT) { // But we should not interrupt. interruptCount++;
         * return; } // We show an alert anyway if (!installdata.canClose)
         * JOptionPane.showMessageDialog(null, langpack.getString("installer.quit.message"),
         * langpack.getString("installer.warning"), JOptionPane.ERROR_MESSAGE); wipeAborted();
         * Housekeeper.getInstance().shutDown(0); }
         */
    }

    /**
     * A FocusTraversalPolicy that only allows the block panel to have the focus
     */
    private class BlockFocusTraversalPolicy extends java.awt.DefaultFocusTraversalPolicy {

        private static final long serialVersionUID = 3258413928261169209L;

        /**
         * Only accepts the block panel
         *
         * @param aComp the component to check
         * @return true if aComp is the block panel
         */
        protected boolean accept(Component aComp) {
            return aComp == getGlassPane();
        }
    }

    /**
     * Returns the gui creation listener list.
     *
     * @return the gui creation listener list
     */
    public List<GUIListener> getGuiListener() {
        return guiListener;
    }

    /**
     * Add a listener to the listener list.
     *
     * @param listener to be added as gui creation listener
     */
    public void addGuiListener(GUIListener listener) {
        guiListener.add(listener);
    }

    /**
     * Creates heading labels.
     *
     * @param headingLines the number of lines of heading labels
     * @param back         background color (currently not used)
     */
    private void createHeadingLabels(int headingLines, Color back) {
        // headingLabels are an array which contains the labels for header (0),
        // description lines and the icon (last).
        headingLabels = new JLabel[headingLines + 1];
        headingLabels[0] = new JLabel("");
        // First line ist the "main heading" which should be bold.
        headingLabels[0].setFont(headingLabels[0].getFont().deriveFont(Font.BOLD));

        // Updated by Daniel Azarov, Exadel Inc.
        // start
        Color foreground = null;
        if (installdata.guiPrefs.modifier.containsKey("headingForegroundColor")) {
            foreground = Color.decode(installdata.guiPrefs.modifier.get("headingForegroundColor"));
            headingLabels[0].setForeground(foreground);
        }
        // end

        if (installdata.guiPrefs.modifier.containsKey("headingFontSize")) {
            float fontSize = Float.parseFloat(installdata.guiPrefs.modifier.get("headingFontSize"));
            if (fontSize > 0.0 && fontSize <= 5.0) {
                float currentSize = headingLabels[0].getFont().getSize2D();
                headingLabels[0].setFont(headingLabels[0].getFont().deriveFont(
                        currentSize * fontSize));
            }
        }
        if (imageLeft) {
            headingLabels[0].setAlignmentX(Component.RIGHT_ALIGNMENT);
        }
        for (int i = 1; i < headingLines; ++i) {
            headingLabels[i] = new JLabel();
            // Minor headings should be a little bit more to the right.
            if (imageLeft) {
                headingLabels[i].setAlignmentX(Component.RIGHT_ALIGNMENT);
            } else {
                headingLabels[i].setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 8));
            }
        }

    }

    /**
     * Creates heading panel counter.
     *
     * @param back             background color
     * @param navPanel         navi JPanel
     * @param leftHeadingPanel left heading JPanel
     */
    private void createHeadingCounter(Color back, JPanel navPanel, JPanel leftHeadingPanel) {
        int i;
        String counterPos = "inHeading";
        if (installdata.guiPrefs.modifier.containsKey("headingPanelCounterPos")) {
            counterPos = installdata.guiPrefs.modifier.get("headingPanelCounterPos");
        }
        // Do not create counter if it should be in the heading, but no heading should be used.
        if (leftHeadingPanel == null && "inHeading".equalsIgnoreCase(counterPos)) {
            return;
        }
        if (installdata.guiPrefs.modifier.containsKey("headingPanelCounter")) {
            headingCounterComponent = null;
            if ("progressbar".equalsIgnoreCase(installdata.guiPrefs.modifier
                    .get("headingPanelCounter"))) {
                JProgressBar headingProgressBar = new JProgressBar();
                headingProgressBar.setStringPainted(true);
                headingProgressBar.setString("");
                headingProgressBar.setValue(0);
                headingCounterComponent = headingProgressBar;
                if (imageLeft) {
                    headingCounterComponent.setAlignmentX(Component.RIGHT_ALIGNMENT);
                }
            } else if ("text".equalsIgnoreCase(installdata.guiPrefs.modifier
                    .get("headingPanelCounter"))) {
                JLabel headingCountPanels = new JLabel(" ");
                headingCounterComponent = headingCountPanels;
                if (imageLeft) {
                    headingCounterComponent.setAlignmentX(Component.RIGHT_ALIGNMENT);
                } else {
                    headingCounterComponent.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 0));
                }

                // Updated by Daniel Azarov, Exadel Inc.
                // start
                Color foreground = null;
                if (installdata.guiPrefs.modifier.containsKey("headingForegroundColor")) {
                    foreground = Color.decode(installdata.guiPrefs.modifier
                            .get("headingForegroundColor"));
                    headingCountPanels.setForeground(foreground);
                }
                // end
            }
            if ("inHeading".equals(counterPos)) {
                leftHeadingPanel.add(headingCounterComponent);
            } else if ("inNavigationPanel".equals(counterPos)) {
                Component[] comps = navPanel.getComponents();
                for (i = 0; i < comps.length; ++i) {
                    if (comps[i].equals(prevButton)) {
                        break;
                    }
                }
                if (i <= comps.length) {
                    navPanel.add(Box.createHorizontalGlue(), i);
                    navPanel.add(headingCounterComponent, i);
                }

            }
        }
    }

    /**
     * Creates heading icon.
     *
     * @param back the color of background around image.
     * @return a panel with heading image.
     */
    private JPanel createHeadingIcon(Color back) {
        // the icon
        ImageIcon icon = null;
        try {
            icon = loadIcon(HEADING_ICON_RESOURCE, 0, true);
        }
        catch (Exception e) {
            // ignore
        }
        JPanel imgPanel = new JPanel();
        imgPanel.setLayout(new BoxLayout(imgPanel, BoxLayout.Y_AXIS));

        // Updated by Daniel Azarov, Exadel Inc.
        // start
        int borderSize = 8;
        if (installdata.guiPrefs.modifier.containsKey("headingImageBorderSize")) {
            borderSize = Integer.parseInt(installdata.guiPrefs.modifier
                    .get("headingImageBorderSize"));
        }
        imgPanel.setBorder(BorderFactory.createEmptyBorder(borderSize, borderSize, borderSize,
                borderSize));
        // end

        if (back != null) {
            imgPanel.setBackground(back);
        }
        JLabel iconLab = new JLabel(icon);
        if (imageLeft) {
            imgPanel.add(iconLab, BorderLayout.WEST);
        } else {
            imgPanel.add(iconLab, BorderLayout.EAST);
        }
        headingLabels[headingLabels.length - 1] = iconLab;
        return (imgPanel);

    }

    /**
     * Creates a Heading in given Panel.
     *
     * @param navPanel a panel
     */
    private void createHeading(JPanel navPanel) {
        headingPanel = null;
        int headingLines = 1;
        // The number of lines can be determined in the config xml file.
        // The first is the header, additonals are descriptions for the header.
        if (installdata.guiPrefs.modifier.containsKey("headingLineCount")) {
            headingLines = Integer.parseInt(installdata.guiPrefs.modifier.get("headingLineCount"));
        }
        Color back = null;
        int i = 0;
        // It is possible to determine the used background color of the heading panel.
        if (installdata.guiPrefs.modifier.containsKey("headingBackgroundColor")) {
            back = Color.decode(installdata.guiPrefs.modifier.get("headingBackgroundColor"));
        }
        // Try to create counter if no heading should be used.
        if (!isHeading(null)) {
            createHeadingCounter(back, navPanel, null);
            return;
        }
        // See if we should switch the header image to the left side
        if (installdata.guiPrefs.modifier.containsKey("headingImageOnLeft")
                && (installdata.guiPrefs.modifier.get("headingImageOnLeft").equalsIgnoreCase("yes") || installdata.guiPrefs.modifier
                .get("headingImageOnLeft").equalsIgnoreCase("true"))) {
            imageLeft = true;
        }
        // We create the text labels and the needed panels. From inner to outer.
        // Labels
        createHeadingLabels(headingLines, back);
        // Panel which contains the labels
        JPanel leftHeadingPanel = new JPanel();
        if (back != null) {
            leftHeadingPanel.setBackground(back);
        }
        leftHeadingPanel.setLayout(new BoxLayout(leftHeadingPanel, BoxLayout.Y_AXIS));
        if (imageLeft) {
            leftHeadingPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        }
        for (i = 0; i < headingLines; ++i) {
            leftHeadingPanel.add(headingLabels[i]);
        }

        // HeadingPanel counter: this is a label or a progress bar which can be placed
        // in the leftHeadingPanel or in the navigation bar. It is facultative. If
        // exist, it shows the current panel number and the amount of panels.
        createHeadingCounter(back, navPanel, leftHeadingPanel);
        // It is possible to place an icon on the right side of the heading panel.
        JPanel imgPanel = createHeadingIcon(back);

        // The panel for text and icon.
        JPanel northPanel = new JPanel();
        if (back != null) {
            northPanel.setBackground(back);
        }
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.X_AXIS));
        northPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        if (imageLeft) {
            northPanel.add(imgPanel);
            northPanel.add(Box.createHorizontalGlue());
            northPanel.add(leftHeadingPanel);
        } else {
            northPanel.add(leftHeadingPanel);
            northPanel.add(Box.createHorizontalGlue());
            northPanel.add(imgPanel);
        }
        headingPanel = new JPanel(new BorderLayout());
        headingPanel.add(northPanel);
        headingPanel.add(new JSeparator(), BorderLayout.SOUTH);

        // contentPane.add(northPanel, BorderLayout.NORTH);
        contentPane.add(headingPanel, BorderLayout.NORTH);

    }

    /**
     * Returns whether this installer frame uses with the given panel a separated heading panel or
     * not. Be aware, this is an other heading as given by the IzPanel which will be placed in the
     * IzPanel. This heading will be placed if the gui preferences contains an modifier with the key
     * "useHeadingPanel" and the value "yes" and there is a message with the key "&lt;class
     * name&gt;.headline".
     *
     * @param caller the IzPanel for which heading should be resolved
     * @return whether an heading panel will be used or not
     */
    public boolean isHeading(IzPanel caller) {
        if (!installdata.guiPrefs.modifier.containsKey("useHeadingPanel")
                || !(installdata.guiPrefs.modifier.get("useHeadingPanel")).equalsIgnoreCase("yes")) {
            return (false);
        }
        if (caller == null) {
            return (true);
        }
        return (caller.getI18nStringForClass("headline", null) != null);

    }

    private void performHeading(IzPanel panel) {
        int i;
        int headingLines = 1;
        if (installdata.guiPrefs.modifier.containsKey("headingLineCount")) {
            headingLines = Integer.parseInt(installdata.guiPrefs.modifier.get("headingLineCount"));
        }

        if (headingLabels == null) {
            return;
        }
        String headline = panel.getI18nStringForClass("headline");
        if (headline == null) {
            headingPanel.setVisible(false);
            return;
        }
        for (i = 0; i <= headingLines; ++i) {
            if (headingLabels[i] != null) {
                headingLabels[i].setVisible(false);
            }
        }
        String info;
        for (i = 0; i < headingLines - 1; ++i) {
            info = panel.getI18nStringForClass("headinfo" + Integer.toString(i));
            if (info == null) {
                info = " ";
            }
            if (info.endsWith(":")) {
                info = info.substring(0, info.length() - 1) + ".";
            }
            headingLabels[i + 1].setText(info);
            headingLabels[i + 1].setVisible(true);
        }
        // Do not forgett the first headline.
        headingLabels[0].setText(headline);
        headingLabels[0].setVisible(true);
        int curPanelNo = panelManager.getPanelVisibilityNumber(installdata.getCurPanelNumber());
        if (headingLabels[headingLines] != null) {
            loadAndShowImage(headingLabels[headingLines], HEADING_ICON_RESOURCE, curPanelNo);
            headingLabels[headingLines].setVisible(true);
        }
        headingPanel.setVisible(true);

    }

    private void performHeadingCounter(IzPanel panel) {
        if (headingCounterComponent != null) {
            int curPanelNo = panelManager.getPanelVisibilityNumber((installdata.getCurPanelNumber()));
            int visPanelsCount = panelManager.getCountVisiblePanel();

            StringBuffer buf = new StringBuffer();
            buf.append(langpack.getString("installer.step")).append(" ").append(curPanelNo + 1)
                    .append(" ").append(langpack.getString("installer.of")).append(" ").append(
                    visPanelsCount + 1);
            if (headingCounterComponent instanceof JProgressBar) {
                JProgressBar headingProgressBar = (JProgressBar) headingCounterComponent;
                headingProgressBar.setMaximum(visPanelsCount + 1);
                headingProgressBar.setValue(curPanelNo + 1);
                headingProgressBar.setString(buf.toString());
            } else {
                ((JLabel) headingCounterComponent).setText(buf.toString());
            }
        }
    }

    /**
     * Shows or hides Help button depending on <code>show</code> parameter
     *
     * @param show - flag to show or hide Help button
     */
    private void showHelpButton(boolean show) {
        if (this.helpButton == null) return;
        this.helpButton.setVisible(show);
    }

    public RulesEngine getRules() {
        return rules;
    }
}
