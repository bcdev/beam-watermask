package org.esa.beam.watermask.ui;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glevel.MultiLevelImage;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import com.jidesoft.action.CommandBar;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.ConvolutionFilterBand;
import org.esa.beam.framework.datamodel.Kernel;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.framework.ui.command.CommandAdapter;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
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
 * This VISAT PlugIn registers an action  which calls the "LandWaterMask" Operator and based on its generated "water_fraction"
 * band, defines 3 masks in the currently selected product:
 * <ol>
 * <li>Water: water_fraction > 90</li>
 * <li>Land: water_fraction < 10</li>
 * <li>Coastline: water_fraction > 0.1 && water_fraction < 99.9 (meaning if water_fraction is not 0 or 100 --> it is a coastline)</li>
 * </ol>
 * <p/>
 * <p/>
 * <i>IMPORTANT Note:
 * This VISAT PlugIn is a workaround.
 * Ideally, users would register an action in BEAM's  {@code module.xml} and specify a target toolbar for it.
 * Actions specified in BEAM's  {@code module.xml} currently only appear in menus, and not in tool bars
 * (because they are hard-coded in VisatApp).
 * Since this feature is still missing in BEAM, so we have to place the action in its target tool bar
 * ("layersToolBar") manually.</i>
 *
 * @author Tonio Fincke
 * @author Danny Knowles
 * @author Marco Peters
 */
public class WaterMaskVPI extends AbstractVisatPlugIn {

    public static final String COMMAND_ID = "createLandWaterCoastMasks";
    public static final String LAND_WATER_MASK_OP_ALIAS = "LandWaterMask";
    public static final String TARGET_TOOL_BAR_NAME = "layersToolBar";
    public static final String WATER_FRACTION_BAND_NAME = "water_fraction";
    public static final String BLURRED_WATER_FRACTION_NAME = "blurred_water_fraction";

    String landExpression = WATER_FRACTION_BAND_NAME + " < 15";
    String coastlineExpression = BLURRED_WATER_FRACTION_NAME + " > 15 and " + BLURRED_WATER_FRACTION_NAME + " < 85";
    String waterExpression = WATER_FRACTION_BAND_NAME + " > 85";

    boolean showCoastline = true;
    boolean showLandMask = false;
    boolean showWaterMask = false;

    @Override
    public void start(final VisatApp visatApp) {
        final ExecCommand action = visatApp.getCommandManager().createExecCommand(COMMAND_ID,
                                                                                  new ToolbarCommand(visatApp));
        action.setLargeIcon(UIUtils.loadImageIcon("/org/esa/beam/watermask/ui/icons/dock.gif"));

        final AbstractButton lwcButton = visatApp.createToolButton(COMMAND_ID);

        visatApp.getMainFrame().addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                CommandBar layersBar = visatApp.getToolBar(TARGET_TOOL_BAR_NAME);
                layersBar.add(lwcButton);
            }

        });
    }

    private void showLandWaterCoastMasks(final VisatApp visatApp) {
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
                                                                                  "Computing Masks") {

            @Override
            protected Void doInBackground(ProgressMonitor pm) throws Exception {
                Product product = visatApp.getSelectedProduct();
                pm.beginTask("Creating land, water, coastline masks", 2);

                try {
//        Product landWaterProduct = GPF.createProduct("LandWaterMask", GPF.NO_PARAMS, product);
                    Map<String, Object> parameters = new HashMap<String, Object>();
                    parameters.put("subSamplingFactorX", 3);
                    parameters.put("subSamplingFactorY", 3);
                    Product landWaterProduct = GPF.createProduct(LAND_WATER_MASK_OP_ALIAS, parameters, product);
                    Band waterFractionBand = landWaterProduct.getBand("land_water_fraction");
                    Band coastBand = landWaterProduct.getBand("coast");

                    // PROBLEM WITH TILE SIZES
                    // Example: product has tileWidth=498 and tileHeight=611
                    // resulting image has tileWidth=408 and tileHeight=612
                    // Why is this happening and where?
                    // For now we change the image layout here.
                    reformatSourceImage(waterFractionBand, new ImageLayout(product.getBandAt(0).getSourceImage()));
                    reformatSourceImage(coastBand, new ImageLayout(product.getBandAt(0).getSourceImage()));

                    pm.worked(1);
                    waterFractionBand.setName(WATER_FRACTION_BAND_NAME);
                    product.addBand(waterFractionBand);
                    product.addBand(coastBand);

                    //todo replace with JAI operator "GeneralFilter" which uses a GeneralFilterFunction
                    final Kernel arithmeticMean3x3Kernel = new Kernel(3, 3, 1.0 / 9.0,
                                                                      new double[]{
                                                                              +1, +1, +1,
                                                                              +1, +1, +1,
                                                                              +1, +1, +1,
                                                                      });
                    final ConvolutionFilterBand filteredCoastlineBand = new ConvolutionFilterBand(BLURRED_WATER_FRACTION_NAME,
                                                                                          waterFractionBand,
                                                                                          arithmeticMean3x3Kernel);
                    product.addBand(filteredCoastlineBand);

                    ProductNodeGroup<Mask> maskGroup = product.getMaskGroup();
                    Mask landMask = Mask.BandMathsType.create("poormans_land", "Land pixels",
                                                              product.getSceneRasterWidth(),
                                                              product.getSceneRasterHeight(),
                                                              landExpression, Color.GREEN.darker(), 0.4);
                    maskGroup.add(landMask);

                    Mask coastlineMask = Mask.BandMathsType.create("poormans_coastline", "Coastline pixels",
                                                                   product.getSceneRasterWidth(),
                                                                   product.getSceneRasterHeight(),
                                                                   coastlineExpression,
                                                                   Color.YELLOW, 0.2);
                    maskGroup.add(coastlineMask);

                    Mask waterMask = Mask.BandMathsType.create("poormans_water", "Water pixels",
                                                               product.getSceneRasterWidth(),
                                                               product.getSceneRasterHeight(),
                                                               waterExpression, Color.BLUE, 0.4);
                    maskGroup.add(waterMask);
                    pm.worked(1);

                    String[] bandNames = product.getBandNames();
                    for (String bandName : bandNames) {
                        RasterDataNode raster = product.getRasterDataNode(bandName);
                        if (showCoastline) {
                            raster.getOverlayMaskGroup().add(coastlineMask);
                        }
                        if (showLandMask) {
                            raster.getOverlayMaskGroup().add(landMask);
                        }
                        if (showWaterMask) {
                            raster.getOverlayMaskGroup().add(waterMask);
                        }
                    }

//                    visatApp.setSelectedProductNode(waterFractionBand);

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

    private class ToolbarCommand extends CommandAdapter {
        private final VisatApp visatApp;

        public ToolbarCommand(VisatApp visatApp) {
            this.visatApp = visatApp;
        }

        @Override
        public void actionPerformed(
                CommandEvent event) {
            showLandWaterCoastMasks(
                    visatApp);

        }

        @Override
        public void updateState(
                CommandEvent event) {
            Product selectedProduct = visatApp.getSelectedProduct();
            boolean productSelected = selectedProduct != null;
            boolean hasBands = false;
            boolean hasGeoCoding = false;
            if (productSelected) {
                hasBands = selectedProduct.getNumBands() > 0;
                hasGeoCoding = selectedProduct.getGeoCoding() != null;
            }
            event.getCommand().setEnabled(
                    productSelected && hasBands && hasGeoCoding);
        }
    }
}
