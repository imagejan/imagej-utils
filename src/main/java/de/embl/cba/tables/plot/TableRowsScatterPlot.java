package de.embl.cba.tables.plot;

import bdv.util.*;
import bdv.viewer.Source;
import de.embl.cba.bdv.utils.BdvUtils;
import de.embl.cba.bdv.utils.popup.BdvPopupMenus;
import de.embl.cba.bdv.utils.sources.ARGBConvertedRealAccessibleSource;
import de.embl.cba.swing.PopupMenu;
import de.embl.cba.tables.Outlier;
import de.embl.cba.tables.Utils;
import de.embl.cba.tables.color.ColorUtils;
import de.embl.cba.tables.color.ListItemsARGBConverter;
import de.embl.cba.tables.color.SelectionColoringModel;
import de.embl.cba.tables.select.SelectionModel;
import de.embl.cba.tables.tablerow.TableRow;
import de.embl.cba.tables.view.Globals;
import ij.gui.GenericDialog;
import net.imglib2.*;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.ui.TransformListener;
import net.imglib2.util.Intervals;
import org.apache.commons.lang.mutable.MutableDouble;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiConsumer;

public class TableRowsScatterPlot< T extends TableRow >
{
	private final List< T > tableRows;
	private Interval dataPlotInterval;
	private int numTableRows;
	private final SelectionColoringModel< T > coloringModel;
	private final SelectionModel< T > selectionModel;
	private BdvHandle bdvHandle;
	private ArrayList< RealPoint > points;
	private ArrayList< Integer > indices;
	private double viewerPointSize;
	private Source< VolatileARGBType > argbSource;
	private NearestNeighborSearchOnKDTree< Integer > search;
	private String columnNameX;
	private String columnNameY;
	private BdvStackSource< VolatileARGBType > scatterPlotBdvSource;
	private final String[] columnNames;
	private String[] lineChoices;
	private String lineOverlay;
	private double viewerAspectRatio = 1.0;
	private RealRandomAccessibleIntervalSource indexSource;
	private FinalRealInterval dataInterval;
	private double[] dataRanges;
	private double dataAspectRatio;
	private ArrayList< RealPoint> viewerPoints;
	private AffineTransform3D viewerTransform;
	private String name;
	private HashMap< String, Double > xLabelToIndex;
	private HashMap< String, Double > yLabelToIndex;
	private SelectedPointOverlay selectedPointOverlay;
	private ArrayList< HashMap< String, Double > > labelsToIndices;
	private int axisLabelsFontSize;

	public TableRowsScatterPlot(
			List< T > tableRows,
			String name,
			SelectionColoringModel< T > coloringModel,
			SelectionModel< T > selectionModel,
			String columnNameX,
			String columnNameY,
			String lineOverlay,
			int axisLabelsFontSize )
	{
		this.tableRows = tableRows;
		this.name = name;
		this.coloringModel = coloringModel;
		this.selectionModel = selectionModel;
		this.columnNameX = columnNameX;
		this.columnNameY = columnNameY;

		numTableRows = tableRows.size();
		columnNames = tableRows.get( 0 ).getColumnNames().stream().toArray( String[]::new );
		this.axisLabelsFontSize = axisLabelsFontSize;

		coloringModel.listeners().add( () -> {
			bdvHandle.getViewerPanel().requestRepaint();
		} );

		this.lineOverlay = lineOverlay;
	}

	private void createAndShowImage( int x, int y )
	{
		fetchDataPoints( columnNameX, columnNameY );

		if ( points.size() < 1 )
		{
			throw new UnsupportedOperationException( "Cannot create scatter plot \"" + name + "\", because there is no valid data point." );
		}

		createSearchTree();

		setViewerAspectRatio();

		setViewerPointSize();

		BiConsumer< RealLocalizable, IntType > biConsumer = createPlotFunction();

		createSource( biConsumer );

		showSource();

		registerAsViewerTransformListener();

		setViewerTransform();

		installBdvBehaviours();

		setWindowPosition( x, y );

		addGridLinesOverlay();

		addAxisTickLabelsOverlay();

		addSelectedPointsOverlay();
	}

	private void registerAsViewerTransformListener()
	{
		bdvHandle.getViewerPanel().addTransformListener( new TransformListener< AffineTransform3D >()
		{
			@Override
			public void transformChanged( AffineTransform3D affineTransform3D )
			{
				synchronized ( this )
				{
					viewerTransform = affineTransform3D;
					createViewerSearchTree( viewerTransform );
				}
			}
		} );
	}

	private void setViewerPointSize()
	{
		viewerPointSize = 7; // TODO: ?
	}

	private void setViewerAspectRatio()
	{
		viewerAspectRatio = 1 / dataAspectRatio;

//		if ( dataAspectRatio < 0.2  || dataAspectRatio > 1 / 0.2 )
//		{
//			viewerAspectRatio = 1 / dataAspectRatio;
//		}
//		else
//		{
//			viewerAspectRatio = 1.0;
//		}
	}

	private void createSearchTree()
	{
		// Give a copy because the order of the list is changed by the KDTree
		final ArrayList< RealPoint > copy = new ArrayList<>( points );
		final KDTree< Integer > kdTree = new KDTree<>( indices, copy );
		search = new NearestNeighborSearchOnKDTree<>( kdTree );
	}

	private void createViewerSearchTree( AffineTransform3D transform3D )
	{
		for ( int i = 0; i < points.size(); i++ )
		{
			transform3D.apply( points.get( i ), viewerPoints.get( i ) );
		}

		final KDTree< Integer > kdTree = new KDTree<>( indices, viewerPoints );
		search = new NearestNeighborSearchOnKDTree<>( kdTree );
	}

	private void addSelectedPointsOverlay()
	{
		if ( selectedPointOverlay != null )
			selectedPointOverlay.close();

		selectedPointOverlay = new SelectedPointOverlay( this );

		BdvFunctions.showOverlay( selectedPointOverlay, "selected point overlay", BdvOptions.options().addTo( bdvHandle ).is2D() );
	}

	private void addGridLinesOverlay()
	{
		GridLinesOverlay gridLinesOverlay = new GridLinesOverlay( bdvHandle, columnNameX, columnNameY, dataPlotInterval, lineOverlay, axisLabelsFontSize );

		BdvFunctions.showOverlay( gridLinesOverlay, "grid lines overlay", BdvOptions.options().addTo( bdvHandle ).is2D() );
	}

	private void addAxisTickLabelsOverlay()
	{
		AxisTickLabelsOverlay scatterPlotGridLinesOverlay = new AxisTickLabelsOverlay( xLabelToIndex, yLabelToIndex, dataInterval );

		BdvFunctions.showOverlay( scatterPlotGridLinesOverlay, "axis tick labels overlay", BdvOptions.options().addTo( bdvHandle ).is2D() );
	}

	private void installBdvBehaviours()
	{
		Behaviours behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.install( bdvHandle.getTriggerbindings(), "plate viewer" );

		BdvPopupMenus.addAction( bdvHandle, "Focus closest point",
				( x, y ) -> {
					final RealPoint mouse3d = new RealPoint( x,y, 0 );
					search.search( mouse3d ); // TODO: why is this 3d??
					final Integer rowIndex = search.getSampler().get();
					final T tableRow = tableRows.get( rowIndex );
					selectionModel.focus( tableRow );
				}
		);

		BdvPopupMenus.addAction( bdvHandle, "Change columns...",
				( x, y ) -> {
					lineChoices = new String[]{ GridLinesOverlay.NONE, GridLinesOverlay.Y_NX, GridLinesOverlay.Y_N };

					final GenericDialog gd = new GenericDialog( "Column selection" );
					gd.addChoice( "Column X", columnNames, columnNameX );
					gd.addChoice( "Column Y", columnNames, columnNameY );
					gd.addChoice( "Add lines", lineChoices, GridLinesOverlay.NONE );
					gd.showDialog();

					if ( gd.wasCanceled() ) return;

					columnNameX = gd.getNextChoice();
					columnNameY = gd.getNextChoice();
					lineOverlay = gd.getNextChoice();

					final int xLoc = SwingUtilities.getWindowAncestor( bdvHandle.getViewerPanel() ).getLocationOnScreen().x;
					final int yLoc = SwingUtilities.getWindowAncestor( bdvHandle.getViewerPanel() ).getLocationOnScreen().y;

					bdvHandle.close();

					createAndShowImage( xLoc, yLoc );
				}
		);
	}

	private RealPoint getViewerMouse3dPosition()
	{
		final RealPoint mouse2d = new RealPoint( 0, 0 );
		bdvHandle.getViewerPanel().getMouseCoordinates( mouse2d );
		final RealPoint mouse3d = new RealPoint( 3 );
		for ( int d = 0; d < 2; d++ )
		{
			mouse3d.setPosition( mouse2d.getDoublePosition( d ), d );
		}
		return mouse3d;
	}

	private RealPoint getMouseGlobal2dLocation()
	{
		final RealPoint global3dLocation = new RealPoint( 3 );
		bdvHandle.getViewerPanel().getGlobalMouseCoordinates( global3dLocation );
		final RealPoint dataPosition = new RealPoint( global3dLocation.getDoublePosition( 0 ), global3dLocation.getDoublePosition( 1 ) );
		return dataPosition;
	}

	public void fetchDataPoints( String columnNameX, String columnNameY )
	{
		points = new ArrayList<>();
		viewerPoints = new ArrayList<>();
		indices = new ArrayList<>();

		Double x, y;
		Double xMax=-Double.MAX_VALUE,yMax=-Double.MAX_VALUE,xMin=Double.MAX_VALUE,yMin=Double.MAX_VALUE;

		xLabelToIndex = new HashMap<>();
		MutableDouble xCategoricalIndex = new MutableDouble( 0.0 );

		yLabelToIndex = new HashMap<>();
		MutableDouble yCategoricalIndex = new MutableDouble( 0.0 );

		labelsToIndices = new ArrayList<>();
		labelsToIndices.add( xLabelToIndex );
		labelsToIndices.add( yLabelToIndex );

		for ( int rowIndex = 0; rowIndex < numTableRows; rowIndex++ )
		{
			final T tableRow = tableRows.get( rowIndex );

			if ( tableRow instanceof Outlier )
				if ( ( ( Outlier ) tableRow ).isOutlier() )
					continue;

			x = getDouble( tableRow.getCell( columnNameX ), xLabelToIndex, xCategoricalIndex );
			if ( x == null ) continue;
			y = getDouble( tableRow.getCell( columnNameY ), yLabelToIndex, yCategoricalIndex );
			if ( y == null ) continue;

			points.add( new RealPoint( x, y, 0 ) );
			viewerPoints.add( new RealPoint( 0, 0, 0 ) );
			indices.add( rowIndex );
			if ( x > xMax ) xMax = x;
			if ( y > yMax ) yMax = y;
			if ( x < xMin ) xMin = x;
			if ( y < yMin ) yMin = y;
		}

		dataInterval = FinalRealInterval.createMinMax(
				xMin * 0.9,
				yMin * 0.9,
				0,
				xMax * 1.1,
				yMax * 1.1,
				0 );

		dataRanges = new double[ 2 ];
		for ( int d = 0; d < 2; d++ )
		{
			dataRanges[ d ] = dataInterval.realMax( d ) - dataInterval.realMin( d );
		}

		dataAspectRatio = dataRanges[ 1 ] / dataRanges[ 0 ];

	}

	private Double getDouble( String cell, HashMap< String, Double > stringToDouble, MutableDouble nextIndex )
	{
		try
		{
			Double x = Utils.parseDouble( cell );
			if ( x.isNaN() ) return null;
			return x;
		}
		catch ( Exception e )
		{
			if ( ! stringToDouble.containsKey( cell ) )
			{
				stringToDouble.put( cell, nextIndex.doubleValue() );
				nextIndex.increment();
			}

			return stringToDouble.get( cell );
		}
	}

	public Double getLocation( String cell, int dimension )
	{
		if ( labelsToIndices.get( dimension ).containsKey( cell ) )
		{
			return labelsToIndices.get( dimension ).get( cell );
		}
		else
		{
			return Utils.parseDouble( cell );
		}
	}

	public Double getLocationX( String cell )
	{
		if ( xLabelToIndex.containsKey( cell ) )
		{
			return xLabelToIndex.get( cell );
		}
		else
		{
			return Utils.parseDouble( cell );
		}
	}

	public BiConsumer< RealLocalizable, IntType > createPlotFunction()
	{
		final double squaredViewerPointSize = viewerPointSize * viewerPointSize;
		final RealPoint dataPoint = new RealPoint( 0, 0, 0 );
		final RealPoint viewerPoint = new RealPoint( 0, 0, 0 );

		return ( p, t ) ->
		{
			synchronized ( this )
			{
				if ( viewerTransform == null ) return;

				for ( int d = 0; d < 2; d++ )
				{
					dataPoint.setPosition( p.getDoublePosition( d ), d );
				}

				viewerTransform.apply( dataPoint, viewerPoint );

				search.search( viewerPoint );

				final double sqrDistance = sqrDistance( viewerPoint, search.getPosition() );

				if ( sqrDistance < squaredViewerPointSize  )
				{
					t.set( search.getSampler().get() );
				}
				else
				{
					t.set( -1 );
				}
			}
		};
	}

	final public static double sqrDistance( final RealLocalizable position1, final RealLocalizable position2 )
	{
		double distSqr = 0;

		final int n = position1.numDimensions();
		for ( int d = 0; d < n; ++d )
		{
			final double pos = position2.getDoublePosition( d ) - position1.getDoublePosition( d );

			distSqr += pos * pos;
		}

		return distSqr;
	}

	private void showSource()
	{
		Prefs.showMultibox( false );

		scatterPlotBdvSource = BdvFunctions.show(
				argbSource,
				BdvOptions.options()
						.is2D()
						.frameTitle( name )
						.preferredSize( Globals.proposedComponentWindowWidth(), Globals.proposedComponentWindowWidth() )
						.transformEventHandlerFactory( new BehaviourTransformEventHandlerPlanar
						.BehaviourTransformEventHandlerPlanarFactory() ) );

		bdvHandle = scatterPlotBdvSource.getBdvHandle();

		scatterPlotBdvSource.setDisplayRange( 0, 255);
	}

	public void createSource( BiConsumer< RealLocalizable, IntType > biConsumer )
	{
		dataPlotInterval = Intervals.smallestContainingInterval( dataInterval );
		dataPlotInterval = Intervals.expand( dataPlotInterval, (int) ( 10 * viewerPointSize ) );

		// make 3D
		dataPlotInterval = FinalInterval.createMinMax(
				dataPlotInterval.min( 0 ),
				dataPlotInterval.min( 1 ),
				0,
				dataPlotInterval.max( 0 ),
				dataPlotInterval.max( 1 ),
				0 );

		final FunctionRealRandomAccessible< IntType > fra = new FunctionRealRandomAccessible< IntType >( 2, biConsumer, IntType::new );

		// make 3D
		final RealRandomAccessible< IntType > rra = RealViews.addDimension( fra );

		indexSource = new RealRandomAccessibleIntervalSource( rra, dataPlotInterval, new IntType(  ), name );

		//scatterSource.getInterpolatedSource(  )

		final ListItemsARGBConverter< T > converter =
				new ListItemsARGBConverter( tableRows, coloringModel );

		converter.getIndexToColor().put( -1, ColorUtils.getARGBType( Color.GRAY ).get() );

		argbSource = new ARGBConvertedRealAccessibleSource( indexSource, converter );
	}

	public void setViewerTransform()
	{
		viewerTransform = new AffineTransform3D();
		// bdvHandle.getViewerPanel().getState().getViewerTransform( viewerTransform );

		AffineTransform3D reflectY = new AffineTransform3D();
		reflectY.set( -1.0, 1, 1 );
		viewerTransform.preConcatenate( reflectY );

		final AffineTransform3D scale = new AffineTransform3D();

		final double scaleX = 1.0 * BdvUtils.getBdvWindowWidth( bdvHandle ) / dataRanges[ 0 ];

		final double zoom = 1.0;
		scale.scale( zoom * scaleX, zoom * scaleX * viewerAspectRatio, 1.0  );
		viewerTransform.preConcatenate( scale );

		FinalRealInterval scaledBounds = viewerTransform.estimateBounds( dataInterval );

		shiftViewerTransformToDataCenter();

		FinalRealInterval finalBounds = viewerTransform.estimateBounds( dataInterval );

		bdvHandle.getViewerPanel().setCurrentViewerTransform( viewerTransform );
	}

	public void shiftViewerTransformToDataCenter()
	{
		FinalRealInterval bounds = viewerTransform.estimateBounds( dataInterval );
		final AffineTransform3D translate = new AffineTransform3D();
		translate.translate( - ( bounds.realMin( 0 ) ), - ( bounds.realMin( 1 ) ), 0 );
		viewerTransform.preConcatenate( translate );
		bounds = viewerTransform.estimateBounds( dataInterval );

		final double[] translation = new double[ 2 ];
		translation[ 0 ] = 0.5 * ( BdvUtils.getBdvWindowWidth( bdvHandle ) - bounds.realMax( 0 ) );
		translation[ 1 ] = 0.5 * ( BdvUtils.getBdvWindowHeight( bdvHandle ) - bounds.realMax( 1 ));

		final AffineTransform3D translate2 = new AffineTransform3D();
		translate2.translate( translation[ 0 ], translation[ 1 ], 0 );
		viewerTransform.preConcatenate( translate2 );
	}

	public void show( JComponent component )
	{
		if ( component != null )
		{
			JFrame topFrame = ( JFrame ) SwingUtilities.getWindowAncestor( component );
			final int x = topFrame.getLocationOnScreen().x + component.getWidth() + 10;
			final int y = topFrame.getLocationOnScreen().y;
			createAndShowImage( x, y );
		}
		else
		{
			createAndShowImage( 10, 10 );
		}
	}

	public void setWindowPosition( int x, int y )
	{
		BdvUtils.getViewerFrame( bdvHandle ).setLocation( x, y );
	}

	public List< T > getTableRows()
	{
		return tableRows;
	}

	public BdvHandle getBdvHandle()
	{
		return bdvHandle;
	}

	public SelectionModel< T > getSelectionModel()
	{
		return selectionModel;
	}

	public ArrayList< RealPoint > getPoints()
	{
		return points;
	}

	public String getColumnNameX()
	{
		return columnNameX;
	}

	public String getColumnNameY()
	{
		return columnNameY;
	}
}
