package org.opentripplanner.api.common;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.xml.datatype.DatatypeConfigurationException;

import org.opentripplanner.routing.core.OptimizeType;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.core.TraverseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class defines all the JAX-RS query parameters for a path search as fields, allowing them to 
 * be inherited by other REST resource classes (the trip planner and the Analyst WMS or tile 
 * resource). They will be properly included in API docs generated by Enunciate. This implies that
 * the concrete REST resource subclasses will be request-scoped rather than singleton-scoped.
 * 
 * @author abyrd
 */
public abstract class SearchResource { // RoutingResource

    private static final Logger LOG = LoggerFactory.getLogger(SearchResource.class);

    /** The start location -- either latitude, longitude pair in degrees or a Vertex
     *  label. For example, <code>40.714476,-74.005966</code> or
     *  <code>mtanyctsubway_A27_S</code>.  */
    @QueryParam("fromPlace") List<String> fromPlace;

    /** The end location (see fromPlace for format). */
    @QueryParam("toPlace") List<String> toPlace;

    /** An unordered list of intermediate locations to be visited (see the fromPlace for format). */
    @QueryParam("intermediatePlaces") List<String> intermediatePlaces;
    
    /** Whether or not the order of intermediate locations is to be respected (TSP vs series). */
    @DefaultValue("false") @QueryParam("intermediatePlacesOrdered") Boolean intermediatePlacesOrdered;
    
    /** The date that the trip should depart (or arrive, for requests where arriveBy is true). */
    @QueryParam("date") List<String> date;
    
    /** The time that the trip should depart (or arrive, for requests where arriveBy is true). */
    @QueryParam("time") List<String> time;
    
    /** Router ID used when in multiple graph mode. Unused in singleton graph mode. */
    @DefaultValue("") @QueryParam("routerId") List<String> routerId;
    
    /** Whether the trip should depart or arrive at the specified date and time. */
    @DefaultValue("false") @QueryParam("arriveBy") List<Boolean> arriveBy;
    
    /** Whether the trip must be wheelchair accessible. */
    @DefaultValue("false") @QueryParam("wheelchair") List<Boolean> wheelchair;

    /** The maximum distance (in meters) the user is willing to walk. Defaults to approximately 1/2 mile. */
    @DefaultValue("800") @QueryParam("maxWalkDistance") List<Double> maxWalkDistance;

    /** The user's walking speed in meters/second. Defaults to approximately 3 MPH. */
    @QueryParam("walkSpeed") List<Double> walkSpeed;

    /** For bike triangle routing, how much safety matters (range 0-1). */
    @QueryParam("triangleSafetyFactor") List<Double> triangleSafetyFactor;
    
    /** For bike triangle routing, how much slope matters (range 0-1). */
    @QueryParam("triangleSlopeFactor") List<Double> triangleSlopeFactor;
    
    /** For bike triangle routing, how much time matters (range 0-1). */            
    @QueryParam("triangleTimeFactor") List<Double> triangleTimeFactor;

    /** The set of characteristics that the user wants to optimize for. @See OptimizeType */
    @DefaultValue("QUICK") @QueryParam("optimize") List<OptimizeType> optimize;
    
    /** The set of modes that a user is willing to use. */
    @DefaultValue("TRANSIT,WALK") @QueryParam("mode") List<TraverseModeSet> modes;

    /** The minimum time, in seconds, between successive trips on different vehicles.
     *  This is designed to allow for imperfect schedule adherence.  This is a minimum;
     *  transfers over longer distances might use a longer time. */
    @DefaultValue("240") @QueryParam("minTransferTime") List<Integer> minTransferTime;

    /** The maximum number of possible itineraries to return. */
    @DefaultValue("3") @QueryParam("numItineraries") List<Integer> numItineraries;

    /** The list of preferred routes.  The format is agency_route, so TriMet_100. */
    @DefaultValue("") @QueryParam("preferredRoutes") List<String> preferredRoutes;
    
    /** The list of unpreferred routes.  The format is agency_route, so TriMet_100. */
    @DefaultValue("") @QueryParam("unpreferredRoutes") List<String> unpreferredRoutes;

    /** Whether intermediate stops -- those that the itinerary passes in a vehicle, but 
     *  does not board or alight at -- should be returned in the response.  For example,
     *  on a Q train trip from Prospect Park to DeKalb Avenue, whether 7th Avenue and
     *  Atlantic Avenue should be included. */
    @DefaultValue("false") @QueryParam("showIntermediateStops") List<Boolean> showIntermediateStops;

    /** The list of banned routes.  The format is agency_route, so TriMet_100. */
    @DefaultValue("") @QueryParam("bannedRoutes") List<String> bannedRoutes;

    /** An additional penalty added to boardings after the first.  The value is in OTP's
     *  internal weight units, which are roughly equivalent to seconds.  Set this to a high
     *  value to discourage transfers.  Of course, transfers that save significant
     *  time or walking will still be taken.*/
    @DefaultValue("0") @QueryParam("transferPenalty") List<Integer> transferPenalty;
    
    /** The maximum number of transfers (that is, one plus the maximum number of boardings)
     *  that a trip will be allowed.  Larger values will slow performance, but could give
     *  better routes.  This is limited on the server side by the MAX_TRANSFERS value in
     *  org.opentripplanner.api.ws.Planner. */
    @DefaultValue("2") @QueryParam("maxTransfers") List<Integer> maxTransfers;

    /** If true, goal direction is turned off and a full path tree is built (specify only once) */
    @DefaultValue("false") @QueryParam("batch") Boolean batch;
    
    /* Inject the servlet request so we have access to the query parameter map */
    @Context protected HttpServletRequest httpServletRequest;

    /** 
     * Build the 0th Request object from the query parameter lists. 
     * @throws ParameterException when there is a problem interpreting a query parameter
     */
    protected TraverseOptions buildRequest() throws ParameterException {
        return buildRequest(0);
    }
    
    /** 
     * Range/sanity check the query parameter fields and build a Request object from them.
     * @param  n allows building several request objects from the same query parameters, 
     *         re-specifying only those parameters that change from one request to the next. 
     * @throws ParameterException when there is a problem interpreting a query parameter
     */
    protected TraverseOptions buildRequest(int n) throws ParameterException {
        TraverseOptions request = new TraverseOptions();
        request.setRouterId(get(routerId, n));
        request.setFrom(get(fromPlace, n));
        request.setTo(get(toPlace, n));
        if (get(date, n) == null && get(time, n) != null) { 
            LOG.debug("parsing ISO datetime {}", get(time, n));
            try { // Full ISO date in time param ?
                request.setDateTime(javax.xml.datatype.DatatypeFactory.newInstance()
                       .newXMLGregorianCalendar(get(time, n)).toGregorianCalendar().getTime());
            } catch (DatatypeConfigurationException e) {
                request.setDateTime(get(date, n), get(time, n));
            }
        } else {
            request.setDateTime(get(date, n), get(time, n));
        }
        request.setWheelchair(get(wheelchair, n));
        if (get(numItineraries, n) != null) {
            request.setNumItineraries(get(numItineraries, n));
        }
        if (get(maxWalkDistance, n) != null) {
            request.setMaxWalkDistance(get(maxWalkDistance, n));
        }
        if (get(walkSpeed, n) != null) {
            request.setSpeed(get(walkSpeed, n));
        }
        OptimizeType opt = get(optimize, n);
        {
            Double tsafe = get(triangleSafetyFactor, n);
            Double tslope = get(triangleSlopeFactor, n);
            Double ttime = get(triangleTimeFactor, n);
            if (tsafe != null || tslope != null || ttime != null ) {
                if (tsafe == null || tslope == null || ttime == null) {
                    throw new ParameterException(Message.UNDERSPECIFIED_TRIANGLE);
                }
                if (opt == null) {
                    opt = OptimizeType.TRIANGLE;
                } else if (opt != OptimizeType.TRIANGLE) {
                    throw new ParameterException(Message.TRIANGLE_OPTIMIZE_TYPE_NOT_SET);
                }
                if (Math.abs(tsafe + tslope + ttime - 1) > Math.ulp(1) * 3) {
                    throw new ParameterException(Message.TRIANGLE_NOT_AFFINE);
                }
                request.setTriangleSafetyFactor(tsafe);
                request.setTriangleSlopeFactor(tslope);
                request.setTriangleTimeFactor(ttime);
            } else if (opt == OptimizeType.TRIANGLE) {
                throw new ParameterException(Message.TRIANGLE_VALUES_NOT_SET);
            }
        }
        if (get(arriveBy, n) != null) {
            request.setArriveBy(get(arriveBy, n));
        }
        if (get(showIntermediateStops, n) != null) {
            request.setShowIntermediateStops(get(showIntermediateStops, n));
        }
        /* intermediate places and their ordering are shared because they are themselves a list */
        if (intermediatePlaces != null && intermediatePlaces.size() > 0 
            && ! intermediatePlaces.get(0).equals("")) {
            request.setIntermediatePlaces(intermediatePlaces);
        }
        if (intermediatePlacesOrdered != null) {
            request.setIntermediatePlacesOrdered(intermediatePlacesOrdered);
        }
        request.setPreferredRoutes(get(preferredRoutes, n));
        request.setUnpreferredRoutes(get(unpreferredRoutes, n));
        request.setBannedRoutes(get(bannedRoutes, n));
        // replace deprecated optimization preference
        // opt has already been assigned above
        if (opt == OptimizeType.TRANSFERS) {
            opt = OptimizeType.QUICK;
            request.setTransferPenalty(get(transferPenalty, n) + 1800);
        } else {
            request.setTransferPenalty(get(transferPenalty, n));
        }
        if (batch != null)
            request.batch = batch;
        request.setOptimize(opt);
        request.setModes(get(modes, n));
        request.setMinTransferTime(get(minTransferTime, n));
        if (get(maxTransfers, n) != null)
            request.setMaxTransfers(get(maxTransfers, n));
        if (intermediatePlaces != null && intermediatePlacesOrdered && request.getModes().isTransit()) {
            throw new UnsupportedOperationException("TSP is not supported for transit trips");
        }
        return request;
    }
    
    /** 
     * @return nth item if it exists, 
     * closest existing item otherwise, or null if the list is null or empty.
     */
    private <T> T get(List<T> l, int i) {
        if (l == null || l.size() == 0)
            return null;
        int maxIndex = l.size() - 1;
        if (i > maxIndex)
            i = maxIndex;
        return l.get(i);
    }

}
