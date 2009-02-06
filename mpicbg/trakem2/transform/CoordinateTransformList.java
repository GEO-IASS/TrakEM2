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
 * 
 * @author Stephan Saalfeld <saalfeld@mpi-cbg.de>
 *
 */
package mpicbg.trakem2.transform;

public class CoordinateTransformList extends mpicbg.models.CoordinateTransformList implements CoordinateTransform
{
	//@Override
	final public void init( final String data )
	{
		throw new NumberFormatException( "There is no parameter based initialisation for " + this.getClass().getCanonicalName() );
	}

	//@Override
	final public String toXML( final String indent )
	{
		String s = indent + "<ict_transform_list>";
		for ( mpicbg.models.CoordinateTransform t : l )
			s += "\n" + indent + ( ( CoordinateTransform )t ).toXML( indent + "\t" );
		return s + "\n" + indent + "</ict_transformlist>";
	}
	
	//@Override
	final public String toDataString()
	{
		return "";
	}
}
