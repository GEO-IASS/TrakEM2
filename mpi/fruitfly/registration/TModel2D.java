package mpi.fruitfly.registration;

import ini.trakem2.utils.Utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.awt.geom.AffineTransform;

public class TModel2D extends AffineModel2D {

	static final private int MIN_SET_SIZE = 1;
	public int getMIN_SET_SIZE(){ return MIN_SET_SIZE; }
	
	@Override
	public float[] apply( float[] point )
	{
		float[] transformed = new float[ 2 ];
		affine.transform( point, 0, transformed, 0, 1 );
		return transformed;
	}
	
	@Override
	public void applyInPlace( float[] point )
	{
		affine.transform( point, 0, point, 0, 1 );
	}
	
	@Override
	public float[] applyInverse( float[] point )
	{
		// the brilliant java.awt.geom.AffineTransform implements transform for float[] but inverseTransform for double[] only...
		double[] double_point = new double[]{ point[ 0 ], point[ 1 ] };
		double[] transformed = new double[ 2 ];
		try
		{
			affine.inverseTransform( double_point, 0, transformed, 0, 1 );
		}
		catch ( Exception e )
		{
			System.err.println( "Noninvertible transformation." );
		}
		return new float[]{ ( float )transformed[ 0 ], ( float )transformed[ 1 ] };
	}

	@Override
	public void applyInverseInPlace( float[] point )
	{
		float[] temp_point = applyInverse( point );
		point[ 0 ] = temp_point[ 0 ];
		point[ 1 ] = temp_point[ 1 ];
	}
	
	@Override
	public boolean fitMinimalSet( PointMatch[] min_matches )
	{
		PointMatch m1 = min_matches[ 0 ];
		
		float[] m1_p1 = m1.getP1().getL(); 
		float[] m1_p2 = m1.getP2().getL(); 
		
		float tx = m1_p1[ 0 ] - m1_p2[ 0 ];
		float ty = m1_p1[ 1 ] - m1_p2[ 1 ];
		
		affine.setToIdentity();
		affine.translate( tx, ty );

		return true;
	}

	@Override
	public String toString()
	{
		return ( "[3,3](" + affine + ") " + error );
	}

	public void fit( Collection< PointMatch > matches )
	{
		// center of mass:
		float xo1 = 0, yo1 = 0;
		float xo2 = 0, yo2 = 0;
		int length = matches.size();
		
		for ( PointMatch m : matches )
		{
			float[] m_p1 = m.getP1().getL(); 
			float[] m_p2 = m.getP2().getW(); 
			
			xo1 += m_p1[ 0 ];
			yo1 += m_p1[ 1 ];
			xo2 += m_p2[ 0 ];
			yo2 += m_p2[ 1 ];
		}
		xo1 /= length;
		yo1 /= length;
		xo2 /= length;
		yo2 /= length;

		float dx = xo1 - xo2; // reversed, because the second will be moved relative to the first
		float dy = yo1 - yo2;
		
		affine.setToIdentity();
		affine.translate( -dx, -dy );
	}
	
	/**
	 * change the model a bit
	 * 
	 * estimates the necessary amount of shaking for each single dimensional
	 * distance in the set of matches
	 * 
	 * @param matches point matches
	 * @param scale gives a multiplicative factor to each dimensional distance (scales the amount of shaking)
	 * @param center local pivot point for centered shakes (e.g. rotation)
	 */
	final public void shake(
			Collection< PointMatch > matches,
			float scale,
			float[] center )
	{
		double xd = 0.0;
		double yd = 0.0;
		
		int num_matches = matches.size();
		if ( num_matches > 0 )
		{
			for ( PointMatch m : matches )
			{
				float[] m_p1 = m.getP1().getW(); 
				float[] m_p2 = m.getP2().getW(); 
				
				xd += Math.abs( m_p1[ 0 ] - m_p2[ 0 ] );;
				yd += Math.abs( m_p1[ 1 ] - m_p2[ 1 ] );;
			}
			xd /= matches.size();
			yd /= matches.size();			
		}
		
		affine.translate(
				rnd.nextGaussian() * ( float )xd * scale,
				rnd.nextGaussian() * ( float )yd );
	}

	public TModel2D clone()
	{
		TModel2D tm = new TModel2D();
		tm.affine.setTransform( affine );
		tm.error = error;
		return tm;
	}
	
	public TRModel2D toTRModel2D()
	{
		TRModel2D trm = new TRModel2D();
		trm.getAffine().setTransform( affine );
		trm.error = error;
		return trm;
	}
}
