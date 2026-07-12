/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pepsoft.worldpainter;

import org.pepsoft.util.DesktopUtils;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.TaskbarProgressReceiver;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.worldpainter.merging.JavaWorldMerger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;

/**
 *
 * @author Pepijn Schmitz
 */
public class MergeProgressDialog extends MultiProgressDialog<Void> implements WindowListener {
    public MergeProgressDialog(Window parent, JavaWorldMerger merger, File backupDir) {
        super(parent, org.pepsoft.worldpainter.WPI18n.s("ui.dialog.merging.title"));
        this.merger = merger;
        this.backupDir = backupDir;
        addWindowListener(this);

        JButton minimiseButton = new JButton(org.pepsoft.worldpainter.WPI18n.s("ui.d27532d90e"));
        minimiseButton.addActionListener(e -> App.getInstance().setState(Frame.ICONIFIED));
        addButton(minimiseButton);
    }

    // WindowListener

    @Override
    public void windowClosed(WindowEvent e) {
        // Make sure to clean up any progress that is still showing
        DesktopUtils.setProgressDone(App.getInstance());
    }

    @Override public void windowClosing(WindowEvent e) {}
    @Override public void windowOpened(WindowEvent e) {}
    @Override public void windowIconified(WindowEvent e) {}
    @Override public void windowDeiconified(WindowEvent e) {}
    @Override public void windowActivated(WindowEvent e) {}
    @Override public void windowDeactivated(WindowEvent e) {}

    // MultiProgressDialog

    @Override
    protected String getVerb() {
        return org.pepsoft.worldpainter.WPI18n.s("merge.verb");
    }

    @Override
    protected String getResultsReport(Void results, long duration) {
        StringBuilder sb = new StringBuilder();
        sb.append(java.text.MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("merge.result.mergedWith"), merger.getMapDir()));
        int hours = (int) (duration / 3600);
        duration = duration - hours * 3600L;
        int minutes = (int) (duration / 60);
        int seconds = (int) (duration - minutes * 60);
        final String durationText = hours + ":" + ((minutes < 10) ? "0" : "") + minutes + ":" + ((seconds < 10) ? "0" : "") + seconds;
        sb.append('\n').append(java.text.MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("merge.result.took"), durationText));
        sb.append("\n\n").append(java.text.MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("merge.result.backupCreated"), backupDir));
        return sb.toString();
    }

    @Override
    protected String getCancellationMessage() {
        return java.text.MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("merge.cancelled.message"), backupDir);
    }

    @Override
    protected ProgressTask<Void> getTask() {
        return new ProgressTask<Void>() {
            @Override
            public String getName() {
                return java.text.MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("merging.world.0"), merger.getWorld().getName());
            }

            @Override
            public Void execute(ProgressReceiver progressReceiver) throws ProgressReceiver.OperationCancelled {
                progressReceiver = new TaskbarProgressReceiver(App.getInstance(), progressReceiver);
                try {
                    merger.merge(backupDir, progressReceiver);
                } catch (IOException e) {
                    throw new RuntimeException("I/O error while merging world " + merger.getWorld().getName() + " with map " + merger.getMapDir(), e);
                }
                return null;
            }
        };
    }

    private final File backupDir;
    private final JavaWorldMerger merger;
}
