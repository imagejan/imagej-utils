package de.embl.cba.tables.plot;


import bdv.util.BdvOverlay;
import net.imglib2.FinalRealInterval;
import net.imglib2.realtransform.AffineTransform2D;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.HashMap;
import java.util.Map;

public class AxisTickLabelsOverlay extends BdvOverlay
{
	private final HashMap< String, Double > xLabelToIndex;
	private final HashMap< String, Double > yLabelToIndex;
	private final FinalRealInterval dataInterval;
	private final double xMin;
	private final double yMin;
	private int offset;
	private int fontSize;

	public AxisTickLabelsOverlay( HashMap< String, Double > xLabelToIndex, HashMap< String, Double > yLabelToIndex, FinalRealInterval dataInterval )
	{
		super();
		this.xLabelToIndex = xLabelToIndex;
		this.yLabelToIndex = yLabelToIndex;
		this.dataInterval = dataInterval;
		xMin = dataInterval.realMin( 0 );
		yMin = dataInterval.realMin( 1 );
		offset = 10;
		fontSize = 15;
	}

	@Override
	protected void draw( final Graphics2D g )
	{
		final AffineTransform2D viewerTransform = new AffineTransform2D();
		this.getCurrentTransform2D( viewerTransform );
		g.setColor( Color.WHITE );

		g.setFont( new Font( "MonoSpaced", Font.PLAIN, fontSize ) );

		AffineTransform orig = g.getTransform();
		//g.rotate(-Math.PI/2);

		final float[] globalLocation = new float[ 2 ];
		final float[] viewerLocation = new float[ 2 ];

		// draw x axis tick marks
		globalLocation[ 1 ] = (float) yMin;
		for ( Map.Entry< String, Double > entry : xLabelToIndex.entrySet() )
		{
			globalLocation[ 0 ] = entry.getValue().floatValue();
			viewerTransform.apply( globalLocation, viewerLocation );
			float xPos = viewerLocation[ 0 ];
			float yPos = viewerLocation[ 1 ];
			final String text = entry.getKey();
			final int stringWidth = g.getFontMetrics().stringWidth( text );
			yPos = yPos - stringWidth + offset;
			g.setTransform( orig );
			g.rotate(-Math.PI/2, xPos, yPos );
			g.drawString( text, xPos, yPos );
		}

		// draw y axis tick marks
		g.setTransform( orig );
		globalLocation[ 0 ] = (float) xMin;
		for ( Map.Entry< String, Double > entry : yLabelToIndex.entrySet() )
		{
			globalLocation[ 1 ] = entry.getValue().floatValue();
			viewerTransform.apply( globalLocation, viewerLocation );
			final String text = entry.getKey();
			final int stringWidth = g.getFontMetrics().stringWidth( text );
			float xPos = viewerLocation[ 0 ];
			xPos = xPos - stringWidth - offset;
			g.drawString( entry.getKey(), xPos, viewerLocation[ 1 ]);
		}
	}
}

