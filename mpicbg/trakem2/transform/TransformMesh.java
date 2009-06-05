/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package mpicbg.trakem2.transform;

import java.awt.Rectangle;
import java.util.Iterator;
import java.util.Set;

import mpicbg.models.CoordinateTransform;
import mpicbg.models.PointMatch;

/**
 *
 * @author Stephan Saalfeld <saalfeld@mpi-cbg.de>
 * @version 0.1b
 */
public class TransformMesh extends mpicbg.models.TransformMesh
{
	final protected Rectangle boundingBox;
	final public Rectangle getBoundingBox(){ return boundingBox; }
	
	public TransformMesh(
			final CoordinateTransform t,
			final int numX,
			final float width,
			final float height )
	{
		super( numX, numY( numX, width, height ), width, height );
		
		final Set< PointMatch > vertices = va.keySet();
		
		/* estimate the bounding box */
		final Iterator< PointMatch > i = vertices.iterator();
		final float[] firstW = i.next().getP2().getW();
		t.applyInPlace( firstW );
		float xMin = firstW[ 0 ], xMax = firstW[ 0 ];
		float yMin = firstW[ 1 ], yMax = firstW[ 1 ];
		while ( i.hasNext() )
		{
			final float[] w = i.next().getP2().getW();
			t.applyInPlace( w );
			
			if ( w[ 0 ] < xMin ) xMin = w[ 0 ];
			else if ( w[ 0 ] > xMax ) xMax = w[ 0 ];
			if ( w[ 1 ] < yMin ) yMin = w[ 1 ];
			else if ( w[ 1 ] > yMax ) yMax = w[ 1 ];
		}
		
		/* shift all points relative to the bounding box */
		for ( final PointMatch vertex : vertices )
		{
			final int tx = ( int )xMin;
			final int ty = ( int )yMin;
			final float[] w = vertex.getP2().getW();
			w[ 0 ] -= tx;
			w[ 1 ] -= ty;
		}
		
		updateAffines();
		
		final float fw = xMax - xMin;
		final float fh = yMax - yMin;
		
		final int w = ( int )fw;
		final int h = ( int )fh;
		
		boundingBox = new Rectangle( ( int )xMin, ( int )yMin, ( w == fw ? w : w + 1 ), ( h == fh ? h : h + 1 ) );
	}
}
