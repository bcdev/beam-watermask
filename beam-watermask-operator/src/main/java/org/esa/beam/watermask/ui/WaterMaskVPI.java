package org.esa.beam.watermask.ui;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glevel.MultiLevelImage;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import com.jidesoft.action.CommandBar;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.framework.ui.command.CommandAdapter;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.AbstractVisatPlugIn;
import org.esa.beam.visat.VisatApp;

import javax.media.jai.ImageLayout;
import javax.media.jai.JAI;
import javax.media.jai.operator.FormatDescriptor;
import javax.swing.AbstractButton;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.RenderedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Marco Peters
 */
public class WaterMaskVPI extends AbstractVisatPlugIn {

    public static final String COMMAND_ID = "landWaterCoast";

    @Override
    public void start(final VisatApp visatApp) {
        visatApp.getCommandManager().createExecCommand(COMMAND_ID, new CommandAdapter() {
            @Override
            public void actionPerformed(CommandEvent event) {
                showLandWaterCoastDialog(visatApp);
            }

            @Override
            public void updateState(CommandEvent event) {
                Product selectedProduct = visatApp.getSelectedProduct();
                event.getCommand().setEnabled(selectedProduct != null);
            }
        });

        final AbstractButton lwcButton = visatApp.createToolButton(COMMAND_ID);

        lwcButton.setIcon(UIUtils.loadImageIcon("/images/dock.gif"));

        visatApp.getMainFrame().addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                CommandBar layersBar = visatApp.getToolBar("layersToolBar");
                layersBar.add(lwcButton);
            }

        });
    }

    private void showLandWaterCoastDialog(final VisatApp visatApp) {
        /*JDialog landWaterCoastDialog = new JDialog();
        landWaterCoastDialog.setVisible(true);

        JPanel lwcPanel = GridBagUtils.createPanel();
        JPanel coastlinePanel = GridBagUtils.createPanel();
        GridBagConstraints coastlineConstraints = new GridBagConstraints();
        int rightInset = 10;

        SpinnerModel transparencyModel = new SpinnerNumberModel(0.4, 0.0, 1.0, 0.1);
        JSpinner transparencySpinner = new JSpinner(transparencyModel);

        SpinnerModel samplingModel = new SpinnerNumberModel(1, 1, 10, 1);
        JSpinner xSamplingSpinner = new JSpinner(samplingModel);
        JSpinner ySamplingSpinner = new JSpinner(samplingModel);

        Integer[] resolutions = {50, 150};
        JComboBox resolutionComboBox = new JComboBox(resolutions);

        GridBagUtils.addToPanel(coastlinePanel, new JCheckBox("Coastline"), coastlineConstraints, "anchor=WEST, gridx=0, gridy=0");
        GridBagUtils.addToPanel(coastlinePanel, new JLabel("Mask name: "), coastlineConstraints, "gridy=1, insets.right="+ rightInset);
        GridBagUtils.addToPanel(coastlinePanel, new JTextField("Coastline"), coastlineConstraints, "gridx=1, insets.right=0");
        GridBagUtils.addToPanel(coastlinePanel, new JLabel("Line color: "), coastlineConstraints, "gridx=0, gridy=2, insets.right=" + rightInset);
        GridBagUtils.addToPanel(coastlinePanel, new ColorExComboBox(), coastlineConstraints, "gridx=1, insets.right=0");
        GridBagUtils.addToPanel(coastlinePanel, new JLabel("Transparency: "), coastlineConstraints, "gridy=2, insets.right="+ rightInset);
        GridBagUtils.addToPanel(coastlinePanel, transparencySpinner, coastlineConstraints, "gridx=1, insets.right=0");
        GridBagUtils.addToPanel(coastlinePanel, new JLabel("Resolution: "), coastlineConstraints, "gridy=3, insets.right="+ rightInset);
        GridBagUtils.addToPanel(coastlinePanel, resolutionComboBox, coastlineConstraints, "gridx=1, insets.right=0");
        GridBagUtils.addToPanel(coastlinePanel, new JLabel("Supersampling factor x: "), coastlineConstraints, "gridy=4, insets.right="+ rightInset);
        GridBagUtils.addToPanel(coastlinePanel, xSamplingSpinner, coastlineConstraints, "gridx=1, insets.right=0");
        GridBagUtils.addToPanel(coastlinePanel, new JLabel("Supersampling factor y: "), coastlineConstraints, "gridy=5, insets.right="+ rightInset);
        GridBagUtils.addToPanel(coastlinePanel, ySamplingSpinner, coastlineConstraints, "gridx=1, insets.right=0");*/


        ProgressMonitorSwingWorker pmSwingWorker = new ProgressMonitorSwingWorker(visatApp.getMainFrame(),
                                                                                  "Computing Land\\Water\\Coast") {

            @Override
            protected Void doInBackground(ProgressMonitor pm) throws Exception {
                Product product = visatApp.getSelectedProduct();
                pm.beginTask("Creating Land\\Water\\Coast", 2);

                try {
//        Product landWaterProduct = GPF.createProduct("LandWaterMask", GPF.NO_PARAMS, product);
                    Map<String, Object> parameters = new HashMap<String, Object>();
                    parameters.put("subSamplingFactorX", 3);
                    parameters.put("subSamplingFactorY", 3);
                    Product landWaterProduct = GPF.createProduct("LandWaterMask", parameters, product);
                    Band waterFraction = landWaterProduct.getBand("land_water_fraction");
                    // Example: product has tileWidth=498 and tileHeight=611
                    // resulting image has tileWidth=408 and tileHeight=612
                    // Why is this happening and where?
                    // For now we change the image layout here.
                    reformatSourceImage(waterFraction, new ImageLayout(product.getBandAt(0).getSourceImage()));
                    pm.worked(1);
                    waterFraction.setName("water_fraction");
                    product.addBand(waterFraction);

                    ProductNodeGroup<Mask> maskGroup = product.getMaskGroup();
                    Mask landMask = Mask.BandMathsType.create("Land", "Land pixels",
                                                              waterFraction.getSceneRasterWidth(),
                                                              waterFraction.getSceneRasterHeight(),
                                                              "water_fraction < 10", Color.GREEN.darker(), 0.4);
                    maskGroup.add(landMask);

                    Mask coastlineMask = Mask.BandMathsType.create("Coastline", "Coastline pixels",
                                                                   waterFraction.getSceneRasterWidth(),
                                                                   waterFraction.getSceneRasterHeight(),
                                                                   "water_fraction >= 10 and water_fraction <= 90",
                                                                   Color.YELLOW, 0.8);
                    maskGroup.add(coastlineMask);

                    Mask waterMask = Mask.BandMathsType.create("Water", "Water pixels",
                                                               waterFraction.getSceneRasterWidth(),
                                                               waterFraction.getSceneRasterHeight(),
                                                               "water_fraction > 90", Color.BLUE, 0.4);
                    maskGroup.add(waterMask);
                    pm.worked(1);

                    String[] bandNames = product.getBandNames();
                    for (String bandName : bandNames) {
                        RasterDataNode raster = product.getRasterDataNode(bandName);
                        raster.getOverlayMaskGroup().add(coastlineMask);
                    }

//        ProductSceneView selectedProductSceneView = visatApp.getSelectedProductSceneView();
//        if (selectedProductSceneView != null) {
//            RasterDataNode raster = selectedProductSceneView.getRaster();
//            raster.getOverlayMaskGroup().add(landMask);
//            raster.getOverlayMaskGroup().add(coastlineMask);
//            raster.getOverlayMaskGroup().add(waterMask);
//        }
                } finally {
                    pm.done();
                }
                return null;
            }


        };

        pmSwingWorker.executeWithBlocking();


    }

    private void reformatSourceImage(Band band, ImageLayout imageLayout) {
        RenderingHints renderingHints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, imageLayout);
        MultiLevelImage waterFractionSourceImage = band.getSourceImage();
        int waterFractionDataType = waterFractionSourceImage.getData().getDataBuffer().getDataType();
        RenderedImage newImage = FormatDescriptor.create(waterFractionSourceImage, waterFractionDataType,
                                                         renderingHints);
        band.setSourceImage(newImage);
    }
}
