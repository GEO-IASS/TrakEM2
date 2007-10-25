//package mpi.fruitfly.registration;

import mpi.fruitfly.general.*;
import mpi.fruitfly.math.datastructures.*;
import mpi.fruitfly.registration.FloatArray2DScaleOctave;
import mpi.fruitfly.registration.FloatArray2DSIFT;
import mpi.fruitfly.registration.TRModel2D;
import mpi.fruitfly.registration.PointMatch;
import mpi.fruitfly.registration.ImageFilter;

import imagescience.transform.*;
import imagescience.image.Image;

import ij.plugin.*;
import ij.gui.*;
import ij.*;
import ij.process.*;

import java.util.Collections;
import java.util.Vector;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import java.io.*;


public class SIFT_Matcher_new implements PlugIn, KeyListener
{
	private static final String[] schemes = {
		"nearest neighbor",
		"linear",
		"cubic convolution",
		"cubic B-spline",
		"cubic O-MOMS",
		"quintic B-spline"
		};
	private static int scheme = 5;

	// steps
	private static int steps = 3;
	// initial sigma
	private static float initial_sigma = 1.6f;
	// background colour
	private static double bg = 0.0;
	// feature descriptor size
	private static int fdsize = 8;
	// feature descriptor orientation bins
	private static int fdbins = 8;
	// size restrictions for scale octaves, use octaves < max_size and > min_size only
	private static int min_size = 64;
	private static int max_size = 1024;
	// minimal allowed alignment error in px
	private static float min_epsilon = 2.0f;
	// maximal allowed alignment error in px
	private static float max_epsilon = 100.0f;
	private static float min_inlier_ratio = 0.05f;
	
	/**
	 * Set true to double the size of the image by linear interpolation to
	 * ( with * 2 + 1 ) * ( height * 2 + 1 ).  Thus we can start identifying
	 * DoG extrema with $\sigma = INITIAL_SIGMA / 2$ like proposed by
	 * \citet{Lowe04}.
	 * 
	 * This is useful for images scmaller than 1000px per side only. 
	 */ 
	private static boolean upscale = false;
	private static float scale = 1.0f;
	
	private static boolean adjust = false;
	private static boolean antialias = true;
	
	/**
	 * show the employed feature correspondences in a small info stack
	 */
	private static boolean show_info = false;

	/**
	 * draw an arbitrarily rotated and scaled ellipse
	 * 
	 * @param evec eigenvectors of unit length ( ev1_x, ev1_y, ev2_x, ev2_y ) define the ellipse's rotation
	 * @param e eigenvalues ( e1, e2 ) define the ellipses size
	 * @param o center of the ellipse ( o_x, o_y )
	 * @param scale scales both, e and o
	 */
	static void drawEllipse( ImageProcessor ip, double[] evec, double[] o, double[] e, double scale )
	{
		int num_keys = 36;
		int[] x_keys = new int[ num_keys + 1 ];
		int[] y_keys = new int[ num_keys + 1 ];
		for ( int i = 0; i < num_keys; ++i )
		{
			double r = ( double )i * 2 * Math.PI / ( double )num_keys;
			double x = Math.sin( r ) * Math.sqrt( Math.abs( e[ 0 ] ) );
			double y = Math.cos( r ) * Math.sqrt( Math.abs( e[ 1 ] ) );
			x_keys[ i ] = ( int )( scale * ( x * evec[ 0 ] + y * evec[ 2 ] + o[ 0 ] ) );
			y_keys[ i ] = ( int )( scale * ( x * evec[ 1 ] + y * evec[ 3 ] + o[ 1 ] ) );
//			System.out.println( "keypoint: ( " + x_keys[ i ] + ", " + y_keys[ i ] + ")" );
		}
		x_keys[ num_keys ] = x_keys[ 0 ];
		y_keys[ num_keys ] = y_keys[ 0 ];
		ip.drawPolygon( new Polygon( x_keys, y_keys, num_keys + 1 ) );
	}

	/**
	 * downscale a grey scale float image using gaussian blur
	 */
	static ImageProcessor downScale( FloatProcessor ip, float s )
	{
		FloatArray2D g = ImageArrayConverter.ImageToFloatArray2D( ip );

		float sigma = ( float )Math.sqrt( 0.25 / s / s - 0.25 );
		float[] kernel = ImageFilter.createGaussianKernel1D( sigma, true );
		
		g = ImageFilter.convolveSeparable( g, kernel, kernel );

		ImageArrayConverter.FloatArrayToFloatProcessor( ip, g );
//		ip.setInterpolate( false );
		return ip.resize( ( int )( s * ip.getWidth() ) );
	}
	
	/**
	 * draws a rotated square with center point  center, having size and orientation
	 */
	static void drawSquare( ImageProcessor ip, double[] o, double scale, double orient )
	{
		scale /= 2;
		
	    double sin = Math.sin( orient );
	    double cos = Math.cos( orient );
	    
	    int[] x = new int[ 6 ];
	    int[] y = new int[ 6 ];
	    

	    x[ 0 ] = ( int )( o[ 0 ] + ( sin - cos ) * scale );
	    y[ 0 ] = ( int )( o[ 1 ] - ( sin + cos ) * scale );
	    
	    x[ 1 ] = ( int )o[ 0 ];
	    y[ 1 ] = ( int )o[ 1 ];
	    
	    x[ 2 ] = ( int )( o[ 0 ] + ( sin + cos ) * scale );
	    y[ 2 ] = ( int )( o[ 1 ] + ( sin - cos ) * scale );
	    x[ 3 ] = ( int )( o[ 0 ] - ( sin - cos ) * scale );
	    y[ 3 ] = ( int )( o[ 1 ] + ( sin + cos ) * scale );
	    x[ 4 ] = ( int )( o[ 0 ] - ( sin + cos ) * scale );
	    y[ 4 ] = ( int )( o[ 1 ] - ( sin - cos ) * scale );
	    x[ 5 ] = x[ 0 ];
	    y[ 5 ] = y[ 0 ];
	    
	    ip.drawPolygon( new Polygon( x, y, x.length ) );
	}

	public void run( String args )
	{
		if ( IJ.versionLessThan( "1.37i" ) ) return;

		final ImagePlus imp = WindowManager.getCurrentImage();
		if ( imp == null )  { System.err.println( "There are no images open" ); return; }
		
		GenericDialog gd = new GenericDialog( "Align stack" );
		gd.addNumericField( "steps_per_scale_octave :", steps, 0 );
		gd.addNumericField( "initial_gaussian_blur :", initial_sigma, 2 );
		gd.addNumericField( "feature_descriptor_size :", fdsize, 0 );
		gd.addNumericField( "feature_descriptor_orientation_bins :", fdbins, 0 );
		gd.addNumericField( "minimum_image_size :", min_size, 0 );
		gd.addNumericField( "maximum_image_size :", max_size, 0 );
		gd.addNumericField( "minimal_alignment_error :", min_epsilon, 2 );
		gd.addNumericField( "maximal_alignment_error :", max_epsilon, 2 );
		gd.addNumericField( "inlier_ratio :", min_inlier_ratio, 2 );
		gd.addNumericField( "background_color :", bg, 2 );
		gd.addChoice( "interpolation_scheme :", schemes, schemes[ scheme ] );
		gd.addCheckbox( "upscale_image_first", upscale );
		gd.addCheckbox( "display_correspondences", show_info );
		gd.showDialog();
		if (gd.wasCanceled()) return;
		
		steps = ( int )gd.getNextNumber();
		initial_sigma = ( float )gd.getNextNumber();
		fdsize = ( int )gd.getNextNumber();
		fdbins = ( int )gd.getNextNumber();
		min_size = ( int )gd.getNextNumber();
		max_size = ( int )gd.getNextNumber();
		min_epsilon = ( float )gd.getNextNumber();
		max_epsilon = ( float )gd.getNextNumber();
		min_inlier_ratio = ( float )gd.getNextNumber();
		bg = ( double )gd.getNextNumber();
		scheme = gd.getNextChoiceIndex();
		upscale = gd.getNextBoolean();
		if ( upscale ) scale = 2.0f;
		else scale = 1.0f;
		show_info = gd.getNextBoolean();
		
		Affine a = new Affine();
		
		int ischeme = Affine.NEAREST;
		switch ( scheme )
		{
		case 0:
			ischeme = Affine.NEAREST;
			break;
		case 1:
			ischeme = Affine.LINEAR;
			break;
		case 2:
			ischeme = Affine.CUBIC;
			break;
		case 3:
			ischeme = Affine.BSPLINE3;
			break;
		case 4:
			ischeme = Affine.OMOMS3;
			break;
		case 5:
			ischeme = Affine.BSPLINE5;
			break;
		}

		ImageStack stack = imp.getStack();
		ImageStack stackAligned = new ImageStack( stack.getWidth(), stack.getHeight() );
		
		float vis_scale = 256.0f / imp.getWidth();
//		float vis_scale = 1024.0f / imp.getWidth();
		ImageStack stackInfo = null;
		ImagePlus impInfo = null;
		
		if ( show_info )
			stackInfo = new ImageStack(
					Math.round( vis_scale * stack.getWidth() ),
					Math.round( vis_scale * stack.getHeight() ) );
		
		stackAligned.addSlice( null, stack.getProcessor( 1 ) );
		ImagePlus impAligned = new ImagePlus( "Aligned 1 of " + stack.getSize(), stackAligned );
		impAligned.show();
		
		ImageProcessor ip1;
		ImageProcessor ip2;
		ImageProcessor ip3 = null;
		
		Vector< FloatArray2DSIFT.Feature > fs1;
		Vector< FloatArray2DSIFT.Feature > fs2;

		ip2 = stack.getProcessor( 1 ).convertToFloat();
		
		AffineTransform at = new AffineTransform();
		
		FloatArray2DSIFT sift = new FloatArray2DSIFT( fdsize, fdbins );
		
		FloatArray2D fa = ImageArrayConverter.ImageToFloatArray2D( ip2 );
		ImageFilter.enhance( fa, 1.0f );
		
		if ( upscale )
		{
			FloatArray2D fat = new FloatArray2D( fa.width * 2 - 1, fa.height * 2 - 1 ); 
			FloatArray2DScaleOctave.upsample( fa, fat );
			fa = fat;
			fa = ImageFilter.computeGaussianFastMirror( fa, ( float )Math.sqrt( initial_sigma * initial_sigma - 1.0 ) );
		}
		else
			fa = ImageFilter.computeGaussianFastMirror( fa, ( float )Math.sqrt( initial_sigma * initial_sigma - 0.25 ) );
		
		long start_time = System.currentTimeMillis();
		System.out.print( "processing SIFT ..." );
		sift.init( fa, steps, initial_sigma, min_size, max_size );
		fs2 = sift.run( max_size );
		Collections.sort( fs2 );
		System.out.println( " took " + ( System.currentTimeMillis() - start_time ) + "ms" );
		
		System.out.println( fs2.size() + " features identified and processed" );
		
		// downscale ip2 for visualisation purposes
		if ( show_info )
			ip2 = downScale( ( FloatProcessor )ip2, vis_scale );
		
		for ( int i = 1; i < stack.getSize(); ++i )
		{
			ip1 = ip2;
			ip2 = stack.getProcessor( i + 1 ).convertToFloat();
			fa = ImageArrayConverter.ImageToFloatArray2D( ip2 );
			ImageFilter.enhance( fa, 1.0f );
			
			if ( upscale )
			{
				FloatArray2D fat = new FloatArray2D( fa.width * 2 - 1, fa.height * 2 - 1 ); 
				FloatArray2DScaleOctave.upsample( fa, fat );
				fa = fat;
				fa = ImageFilter.computeGaussianFastMirror( fa, ( float )Math.sqrt( initial_sigma * initial_sigma - 1.0 ) );
			}
			else
				fa = ImageFilter.computeGaussianFastMirror( fa, ( float )Math.sqrt( initial_sigma * initial_sigma - 0.25 ) );
			
			fs1 = fs2;
			
			start_time = System.currentTimeMillis();
			System.out.print( "processing SIFT ..." );
			sift.init( fa, steps, initial_sigma, min_size, max_size );
			fs2 = sift.run( max_size);
			Collections.sort( fs2 );
			System.out.println( " took " + ( System.currentTimeMillis() - start_time ) + "ms" );
			
			System.out.println( fs2.size() + " features identified and processed");
			
			start_time = System.currentTimeMillis();
			System.out.print( "identifying correspondences using brute force ..." );
			Vector< PointMatch > candidates = 
				FloatArray2DSIFT.createMatches( fs2, fs1, 1.5f, null, Float.MAX_VALUE );
			System.out.println( " took " + ( System.currentTimeMillis() - start_time ) + "ms" );
			
			IJ.log( candidates.size() + " potentially corresponding features identified" );
			
			/**
			 * draw all correspondence candidates
			 */
			if ( show_info )
			{
				ip2 = downScale( ( FloatProcessor )ip2, vis_scale );
			
				ip1 = ip1.convertToRGB();
				ip3 = ip2.convertToRGB();
				ip1.setColor( Color.red );
				ip3.setColor( Color.red );

				ip1.setLineWidth( 2 );
				ip3.setLineWidth( 2 );
				for ( PointMatch m : candidates )
				{
					float[] m_p1 = m.getP1().getL(); 
					float[] m_p2 = m.getP2().getL(); 
					
					ip1.drawDot( ( int )Math.round( vis_scale / scale * m_p2[ 0 ] ), ( int )Math.round( vis_scale / scale * m_p2[ 1 ] ) );
					ip3.drawDot( ( int )Math.round( vis_scale / scale * m_p1[ 0 ] ), ( int )Math.round( vis_scale / scale * m_p1[ 1 ] ) );
				}
			}

			Vector< PointMatch > inliers = new Vector< PointMatch >();
			
			TRModel2D model = TRModel2D.estimateBestModel(
					candidates,
					inliers,
					min_epsilon,
					max_epsilon,
					min_inlier_ratio );
			float epsilon = 0.0f;
			
			if ( model != null )
			{
				if ( show_info )
				{
					ip1.setColor( Color.green );
					ip3.setColor( Color.green );
					ip1.setLineWidth( 2 );
					ip3.setLineWidth( 2 );
					for ( PointMatch m : inliers )
					{
						float[] m_p1 = m.getP1().getL(); 
						float[] m_p2 = m.getP2().getL(); 
						
						ip1.drawDot( ( int )Math.round( vis_scale / scale * m_p2[ 0 ] ), ( int )Math.round( vis_scale / scale * m_p2[ 1 ] ) );
						ip3.drawDot( ( int )Math.round( vis_scale / scale * m_p1[ 0 ] ), ( int )Math.round( vis_scale / scale * m_p1[ 1 ] ) );
					}
				}

				/**
				 * append the estimated transformation model
				 * 
				 * TODO the current rotation assumes the origin (0,0) of the
				 * image in the image's "center"
				 * ( width / 2 - 1.0, height / 2 - 1.0 ).  This is, because we
				 * use imagescience.jar for transformation and they do so...
				 * Think about using an other transformation class, focusing on
				 * better interpolation schemes ( Lanczos would be great ).
				 */
				AffineTransform at_current = new AffineTransform( model.getAffine() );
				double[] m = new double[ 6 ];
				at_current.getMatrix( m );
				m[ 4 ] /= scale;
				m[ 5 ] /= scale;
				at_current.setTransform( m[ 0 ], m[ 1 ], m[ 2 ], m[ 3 ], m[ 4 ], m[ 5 ] );
				
				double hw = ( double )imp.getWidth() / 2.0 - 1.0;
				double hh = ( double )imp.getHeight() / 2.0 - 1.0;
				
				at.translate(
						-hw,
						-hh );
				at.concatenate( at_current );
				at.translate(
						hw,
						hh );
			}
			
			double[] m = new double[ 6 ];
			at.getMatrix( m );

			Image img = Image.wrap( new ImagePlus( "new_layer", stack.getProcessor( i + 1 ) ) );

			Image imgAligned = a.run(
					img,
					new double[][]
					{ { m[ 0 ], m[ 2 ], 0, m[ 4 ] },
					  { m[ 1 ], m[ 3 ], 0, m[ 5 ] },
					  { 0,	  0,	  1, 0 },
					  { 0, 0, 0, 1 } },
					  ischeme,
					  adjust,
					  antialias );
			ImagePlus impAlignedSlice = imgAligned.imageplus();
			stackAligned.addSlice( null, impAlignedSlice.getProcessor() );
			if ( show_info )
			{
				ImageProcessor tmp;
				tmp = ip1.createProcessor( stackInfo.getWidth(), stackInfo.getHeight() );
				tmp.insert( ip1, 0, 0 );
				stackInfo.addSlice( null, tmp ); // fixing silly 1 pixel size missmatches
				tmp = ip3.createProcessor( stackInfo.getWidth(), stackInfo.getHeight() );
				tmp.insert( ip3, 0, 0 );
				stackInfo.addSlice( null, tmp );
				if ( i == 1 )
				{
					impInfo = new ImagePlus( "Alignment info", stackInfo );
					impInfo.show();
				}
				impInfo.setStack( "Alignment info", stackInfo );
				impInfo.updateAndDraw();
			}
			impAligned.setStack( "Aligned " + stackAligned.getSize() + " of " + stack.getSize(), stackAligned );
			impAligned.updateAndDraw();
		}
	}

	public void keyPressed(KeyEvent e)
	{
		if (
				( e.getKeyCode() == KeyEvent.VK_F1 ) &&
				( e.getSource() instanceof TextField ) )
		{
		}
	}

	public void keyReleased(KeyEvent e) { }

	public void keyTyped(KeyEvent e) { }
}
