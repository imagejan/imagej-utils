package explore;

import bdv.ij.util.ProgressWriterIJ;
import bdv.tools.boundingbox.TransformedRealBoxSelectionDialog;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.Interpolation;
import de.embl.cba.bdv.utils.BdvDialogs;
import de.embl.cba.bdv.utils.BdvUtils;
import de.embl.cba.bdv.utils.export.BdvRealSourceToVoxelImageExporter;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlIoSpimData;
import net.imagej.ImageJ;
import net.imglib2.FinalRealInterval;

public class ExploreExportSourcesFromBdv
{
	public void run() throws SpimDataException
	{
		final SpimData spimData = new XmlIoSpimData().load( ExploreExportSourcesFromBdv.class.getResource( "../mri-stack.xml" ).getFile() );

		final BdvStackSource< ? > bdvStackSource =
				BdvFunctions.show( spimData ).get( 0 );

		bdvStackSource.setDisplayRange( 0, 255 );

		final BdvHandle bdvHandle = bdvStackSource.getBdvHandle();

		final SpimData spimData2 = new XmlIoSpimData().load( ExploreExportSourcesFromBdv.class.getResource( "../mri-stack-shifted.xml" ).getFile() );

		BdvFunctions.show( spimData2, BdvOptions.options().addTo( bdvHandle ) ).get( 0 ).setDisplayRange( 0, 255 );

		final FinalRealInterval maximalRangeInterval = BdvUtils.getRealIntervalOfCurrentSource( bdvHandle );

		final TransformedRealBoxSelectionDialog.Result result =
				BdvDialogs.showBoundingBoxDialog(
						bdvHandle,
						maximalRangeInterval );

		final BdvRealSourceToVoxelImageExporter exporter =
				new BdvRealSourceToVoxelImageExporter(
						bdvHandle,
						BdvUtils.getVisibleSourceIndices( bdvHandle ),
						result.getInterval(),
						result.getMinTimepoint(),
						result.getMaxTimepoint(),
						Interpolation.NLINEAR,
						new double[]{ 0.5, 0.5, 0.5 },
						BdvRealSourceToVoxelImageExporter.ExportModality.ShowImages,
						BdvRealSourceToVoxelImageExporter.ExportDataType.UnsignedByte,
						Runtime.getRuntime().availableProcessors(),
						new ProgressWriterIJ()
				);

		exporter.setOutputDirectory( "/Users/tischer/Documents/imagej-utils/src/test/resources/test-output-data" );

		exporter.export();

	}

	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ().ui().showUI();
		new ExploreExportSourcesFromBdv().run();
	}
}
