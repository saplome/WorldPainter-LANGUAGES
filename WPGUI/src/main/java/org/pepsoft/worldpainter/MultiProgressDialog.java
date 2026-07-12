/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * ExportWorldDialog.java
 *
 * Created on Mar 29, 2011, 5:09:50 PM
 */

package org.pepsoft.worldpainter;

import org.pepsoft.minecraft.exception.IncompatibleMaterialException;
import org.pepsoft.util.SubProgressReceiver;
import org.pepsoft.util.swing.ProgressComponent.Listener;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.worldpainter.merging.InvalidMapException;
import org.pepsoft.worldpainter.util.FileInUseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.text.MessageFormat;

import static org.pepsoft.util.AwtUtils.doLaterOnEventThread;
import static org.pepsoft.util.ExceptionUtils.chainContains;
import static org.pepsoft.util.ExceptionUtils.getFromChainOfType;
import static org.pepsoft.util.GUIUtils.scaleToUI;
import static org.pepsoft.util.swing.MessageUtils.*;
import static org.pepsoft.worldpainter.ExceptionHandler.handleException;

/**
 *
 * @author pepijn
 */
public abstract class MultiProgressDialog<T> extends javax.swing.JDialog implements Listener<T>, ComponentListener {
    /** Creates new form ExportWorldDialog */
    public MultiProgressDialog(Window parent, String title) {
        super(parent, ModalityType.APPLICATION_MODAL);
        initComponents();
        localizeButtons(multiProgressComponent1);
        setTitle(title);

        scaleToUI(this);
        setLocationRelativeTo(parent);
        
        addComponentListener(this);
    }

    /**
     * Get the unconjugated verb describing the operation, starting with a
     * capital letter.
     * 
     * @return The unconjugated verb describing the operation.
     */
    protected abstract String getVerb();
    
    /**
     * Transform the results object into a text describing the results, suitable
     * for inclusion in a {@link JOptionPane}. HTML is allowed, and must be
     * enclosed in &lt;html&gt;&lt;/html&gt; tags
     * 
     * @param results The result returned by the task.
     * @param duration The duration in ms.
     * @return A text containing a report of the results.
     */
    protected abstract String getResultsReport(T results, long duration);
    
    /**
     * Get the message to show to the user in a {@link JOptionPane} after they
     * cancel the operation.
     * 
     * @return The message to show to the user in a {@link JOptionPane} after
     * they cancel the operation.
     */
    protected abstract String getCancellationMessage();
    
    /**
     * Get the task to perform. The task may use nested
     * {@link SubProgressReceiver}s to report progress, which will be reported
     * separately on the screen.
     * 
     * @return The task to perform.
     */
    protected abstract ProgressTask<T> getTask();

    /**
     * Add a {@link JButton} to the panel, to the left of the Cancel button.
     *
     * @param button The button to add.
     */
    protected void addButton(JButton button) {
        multiProgressComponent1.addButton(button);
    }

    // ProgressComponent.Listener
    
    @Override
    public void exceptionThrown(Throwable exception) {
        doLaterOnEventThread(() -> {
            if (chainContains(exception, FileInUseException.class)) {
                beepAndShowError(MultiProgressDialog.this, MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.progress.mapInUse.message"), getVerb().toLowerCase()), org.pepsoft.worldpainter.WPI18n.s("ui.progress.mapInUse.title"));
            } else if (chainContains(exception, MissingCustomTerrainException.class)) {
                beepAndShowError(MultiProgressDialog.this,
                        MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.progress.unconfiguredCustomTerrain.message"), (getFromChainOfType(exception, MissingCustomTerrainException.class)).getIndex(), getVerb().toLowerCase()),
                        org.pepsoft.worldpainter.WPI18n.s("ui.progress.unconfiguredCustomTerrain.title"));
            } else if (chainContains(exception, InvalidMapException.class)) {
                beepAndShowError(MultiProgressDialog.this, getFromChainOfType(exception, InvalidMapException.class).getMessage(), org.pepsoft.worldpainter.WPI18n.s("ui.progress.invalidMap.title"));
            } else if (chainContains(exception, IncompatibleMaterialException.class)) {
                beepAndShowError(MultiProgressDialog.this, getFromChainOfType(exception, IncompatibleMaterialException.class).getMessage(), org.pepsoft.worldpainter.WPI18n.s("ui.progress.incompatibleMaterial.title"));
            } else {
                handleException(exception, MultiProgressDialog.this);
            }
            close();
        });
    }

    @Override
    public void done(T result) {
        long end = System.currentTimeMillis();
        long duration = (end - start) / 1000;
        doLaterOnEventThread(() -> {
            String resultsReport = getResultsReport(result, duration);
            showInfo(this, resultsReport, org.pepsoft.worldpainter.WPI18n.s("ui.success.title"));
            close();
        });
    }

    @Override
    public void cancelled() {
        logger.info(getVerb() + " cancelled by user");
        doLaterOnEventThread(() -> {
            showWarning(this, getCancellationMessage(), MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.progress.cancelled.title"), getVerb()));
            close();
        });
    }

    // ComponentListener
    
    @Override
    public void componentShown(ComponentEvent e) {
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        multiProgressComponent1.setTask(getTask());
        multiProgressComponent1.setListener(this);
        start = System.currentTimeMillis();
        multiProgressComponent1.start();
    }

    @Override public void componentResized(ComponentEvent e) {}
    @Override public void componentMoved(ComponentEvent e) {}
    @Override public void componentHidden(ComponentEvent e) {}

    // Implementation details
    
    private void close() {
        dispose();
    }

    private void localizeButtons(Container container) {
        for (Component component: container.getComponents()) {
            if ((component instanceof JButton) && "Cancel".equals(((JButton) component).getText())) {
                ((JButton) component).setText(org.pepsoft.worldpainter.WPI18n.s("ui.button.cancel"));
            }
            if (component instanceof Container) {
                localizeButtons((Container) component);
            }
        }
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        multiProgressComponent1 = new org.pepsoft.util.swing.MultiProgressComponent();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(multiProgressComponent1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(multiProgressComponent1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.pepsoft.util.swing.MultiProgressComponent<T> multiProgressComponent1;
    // End of variables declaration//GEN-END:variables

    private long start;

    private static final Logger logger = LoggerFactory.getLogger(MultiProgressDialog.class);
    private static final long serialVersionUID = 1L;
}
