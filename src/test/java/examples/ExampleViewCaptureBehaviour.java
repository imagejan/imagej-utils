package examples;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvStackSource;
import de.embl.cba.bdv.utils.capture.BdvViewCaptures;
import de.embl.cba.bdv.utils.capture.PixelSpacingDialog;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlIoSpimData;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import java.util.List;

public class ExampleViewCaptureBehaviour
{
	public static void main( String[] args ) throws SpimDataException
	{
		/**
		 * show first image
		 */
		final String path = ExampleViewCaptureBehaviour.class
				.getResource( "../mri-stack.xml" ).getFile();

		SpimData spimData = new XmlIoSpimData().load( path );

		final List< BdvStackSource< ? > > stackSources = BdvFunctions.show( spimData );
		stackSources.get( 0 ).setDisplayRange( 0, 255 );

		final BdvHandle bdv = stackSources.get( 0 ).getBdvHandle();

		/**
		 * add another image
		 */
//		final IntervalView< UnsignedIntType > img = Views.translate( Utils.create2DGradientImage(), new long[]{ 0, 0, 0 } );
////
////		final BdvStackSource stackSource = BdvFunctions.show(
////				img, "image 0",
////				BdvOptions.options().addTo( bdv ) );
////		stackSource.setDisplayRange( 0, 300 );
////		stackSource.setColor( new ARGBType( ARGBType.rgba( 0, 255, 0, 0 ) ) );
////
////		final AffineTransform3D vt = new AffineTransform3D();
////		bdv.getViewerPanel().getState().getViewerTransform( vt );
////		vt.set( 0, 2, 3 );
////		bdv.getViewerPanel().setCurrentViewerTransform( vt );


		/**
		 * install view capture behaviour
		 */
		final String pixelUnit = "unit";

		final PixelSpacingDialog pixelSpacingDialog = new PixelSpacingDialog( 0.25, pixelUnit );

		Behaviours behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.install( bdv.getTriggerbindings(), "" );
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) ->
		{
			new Thread( () -> {
				if ( ! pixelSpacingDialog.showDialog() ) return;
				BdvViewCaptures.captureView(
						bdv,
						pixelSpacingDialog.getPixelSpacing(),
						pixelUnit,
						false );
			}).start();
		}, "capture view", "C" ) ;
	}


}
