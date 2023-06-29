/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -  */
/* Geohash encoding/decoding and associated functions   (c) Chris Veness 2014-2019 / MIT Licence  */
/* converted to JAVA by open.INC GmbH															  */
/* JS-Source available at: https://www.movable-type.co.uk/scripts/geohash.html 																								  */
/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -  */

package de.openinc.openconnect.adapters.mobility;

public class Geohash {

	static String base32 = "0123456789bcdefghjkmnpqrstuvwxyz"; // (geohash-specific) Base32 map
	
	    /**
	     * Encodes latitude/longitude to geohash, either to specified precision or to automatically
	     * evaluated precision.
	     *
	     * @param   {number} lat - Latitude in degrees.
	     * @param   {number} lon - Longitude in degrees.
	     * @param   {number} [precision] - Number of characters in resulting geohash.
	     * @returns {string} Geohash of supplied latitude/longitude.
	     * @throws  Invalid geohash.
	     *
	     * @example
	     *     const geohash = Geohash.encode(52.205, 0.119, 7); // => 'u120fxw'
	     */
	public static String  encode(double lat, double lon, int precision) {
	        if (Double.isNaN(lat) || Double.isNaN(lon) || Double.isNaN(precision)) throw new IllegalArgumentException("Invalid geohash");

	        int idx = 0; // index into base32 map
	        int bit = 0; // each char holds 5 bits
	        boolean evenBit = true;
	        String geohash = "";

	        double latMin =  -90;
	        double latMax =  90;
	        double lonMin = -180;
	        double lonMax = 180;

	        while (geohash.length() < precision) {
	            if (evenBit) {
	                // bisect E-W longitude
	                double lonMid = (lonMin + lonMax) / 2;
	                if (lon >= lonMid) {
	                    idx = idx*2 + 1;
	                    lonMin = lonMid;
	                } else {
	                    idx = idx*2;
	                    lonMax = lonMid;
	                }
	            } else {
	                // bisect N-S latitude
	                double latMid = (latMin + latMax) / 2;
	                if (lat >= latMid) {
	                    idx = idx*2 + 1;
	                    latMin = latMid;
	                } else {
	                    idx = idx*2;
	                    latMax = latMid;
	                }
	            }
	            evenBit = !evenBit;

	            if (++bit == 5) {
	                // 5 bits gives us a character: append it and start over
	                geohash += base32.charAt(idx);
	                bit = 0;
	                idx = 0;
	            }
	        }

	        return geohash;
	    }


	    /**
	     * Decode geohash to latitude/longitude (location is approximate centre of geohash cell,
	     *     to reasonable precision).
	     *
	     * @param   {string} geohash - Geohash string to be converted to latitude/longitude.
	     * @returns {{lat:number, lon:number}} (Center of) geohashed location.
	     * @throws  Invalid geohash.
	     *
	     * @example
	     *     const latlon = Geohash.decode('u120fxw'); // => { lat: 52.205, lon: 0.1188 }
	     */
	    public static double[] decode(String geohash) {

	        double[][] bounds = Geohash.bounds(geohash); // <-- the hard work
	        // now just determine the centre of the cell...

	        double latMin = bounds[0][0];
	        double lonMin = bounds[0][1];
	        double latMax = bounds[1][0];
	        double lonMax = bounds[1][1];

	        // cell centre
	        double lat = (latMin + latMax)/2;
	        double lon = (lonMin + lonMax)/2;

	        // round to close to centre without excessive precision: ⌊2-log10(Δ°)⌋ decimal places
	        lat = Math.floor(2-Math.log(latMax-latMin)/Math.log(10));
	        lon = Math.floor(2-Math.log(lonMax-lonMin)/Math.log(10));

	        return new double[] {lat, lon};
	    }


	    /**
	     * Returns SW/NE latitude/longitude bounds of specified geohash.
	     *
	     * @param   {string} geohash - Cell that bounds are required of.
	     * @returns {{sw: {lat: number, lon: number}, ne: {lat: number, lon: number}}}
	     * @throws  Invalid geohash.
	     */
	   private static double[][] bounds(String geohash) {
	        if (geohash.length() == 0) throw new IllegalArgumentException("Invalid geohash");

	        geohash = geohash.toLowerCase();

	        boolean evenBit = true;
	        double latMin =  -90, latMax =  90;
	        double lonMin = -180, lonMax = 180;

	        for (int i=0; i<geohash.length(); i++) {
	            char chr = geohash.charAt(i);
	            int idx = base32.indexOf(chr);
	            if (idx == -1) throw new IllegalArgumentException("Invalid geohash");

	            for (int n=4; n>=0; n--) {
	                int bitN = idx >> n & 1;
	                if (evenBit) {
	                    // longitude
	                    double lonMid = (lonMin+lonMax) / 2;
	                    if (bitN == 1) {
	                        lonMin = lonMid;
	                    } else {
	                        lonMax = lonMid;
	                    }
	                } else {
	                    // latitude
	                    double latMid = (latMin+latMax) / 2;
	                    if (bitN == 1) {
	                        latMin = latMid;
	                    } else {
	                        latMax = latMid;
	                    }
	                }
	                evenBit = !evenBit;
	            }
	        }

	        
	        double[] sw = new double[] {latMin,lonMin};
	        double[] ne = new double[] {latMax,lonMax};
	        
	        return new double[][]{sw, ne};
	    }


	    /**
	     * Determines adjacent cell in given direction.
	     *
	     * @param   geohash - Cell to which adjacent cell is required.
	     * @param   direction - Direction from geohash (N/S/E/W).
	     * @returns {string} Geocode of adjacent cell.
	     * @throws  Invalid geohash.
	     */
	 /*
	   static adjacent(geohash, direction) {
	        // based on github.com/davetroy/geohash-js

	        geohash = geohash.toLowerCase();
	        direction = direction.toLowerCase();

	        if (geohash.length == 0) throw new Error('Invalid geohash');
	        if ('nsew'.indexOf(direction) == -1) throw new Error('Invalid direction');

	        const neighbour = {
	            n: [ 'p0r21436x8zb9dcf5h7kjnmqesgutwvy', 'bc01fg45238967deuvhjyznpkmstqrwx' ],
	            s: [ '14365h7k9dcfesgujnmqp0r2twvyx8zb', '238967debc01fg45kmstqrwxuvhjyznp' ],
	            e: [ 'bc01fg45238967deuvhjyznpkmstqrwx', 'p0r21436x8zb9dcf5h7kjnmqesgutwvy' ],
	            w: [ '238967debc01fg45kmstqrwxuvhjyznp', '14365h7k9dcfesgujnmqp0r2twvyx8zb' ],
	        };
	        const border = {
	            n: [ 'prxz',     'bcfguvyz' ],
	            s: [ '028b',     '0145hjnp' ],
	            e: [ 'bcfguvyz', 'prxz'     ],
	            w: [ '0145hjnp', '028b'     ],
	        };

	        const lastCh = geohash.slice(-1);    // last character of hash
	        let parent = geohash.slice(0, -1); // hash without last character

	        const type = geohash.length % 2;

	        // check for edge-cases which don't share common prefix
	        if (border[direction][type].indexOf(lastCh) != -1 && parent != '') {
	            parent = Geohash.adjacent(parent, direction);
	        }

	        // append letter for direction to parent
	        return parent + base32.charAt(neighbour[direction][type].indexOf(lastCh));
	    }


	    /**
	     * Returns all 8 adjacent cells to specified geohash.
	     *
	     * @param   {string} geohash - Geohash neighbours are required of.
	     * @returns {{n,ne,e,se,s,sw,w,nw: string}}
	     * @throws  Invalid geohash.
	     //
	    static neighbours(geohash) {
	        return {
	            'n':  Geohash.adjacent(geohash, 'n'),
	            'ne': Geohash.adjacent(Geohash.adjacent(geohash, 'n'), 'e'),
	            'e':  Geohash.adjacent(geohash, 'e'),
	            'se': Geohash.adjacent(Geohash.adjacent(geohash, 's'), 'e'),
	            's':  Geohash.adjacent(geohash, 's'),
	            'sw': Geohash.adjacent(Geohash.adjacent(geohash, 's'), 'w'),
	            'w':  Geohash.adjacent(geohash, 'w'),
	            'nw': Geohash.adjacent(Geohash.adjacent(geohash, 'n'), 'w'),
	        };
	    }

	}
*/
	
	
	
}
