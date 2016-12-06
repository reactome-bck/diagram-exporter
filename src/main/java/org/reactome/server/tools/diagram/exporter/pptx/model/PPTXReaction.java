package org.reactome.server.tools.diagram.exporter.pptx.model;

import com.aspose.slides.*;
import org.reactome.server.tools.diagram.data.layout.Connector;
import org.reactome.server.tools.diagram.data.layout.*;
import org.reactome.server.tools.diagram.data.layout.Shape;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.reactome.server.tools.diagram.exporter.pptx.util.PPTXShape.*;
import static org.reactome.server.tools.diagram.exporter.pptx.util.SegmentUtil.connect;
import static org.reactome.server.tools.diagram.exporter.pptx.util.SegmentUtil.drawSegment;

/**
 * @author Antonio Fabregat <fabregat@ebi.ac.uk>
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
public class PPTXReaction {

    private Edge edge;
    private Map<Long, PPTXNode> nodesMap;

    private IAutoShape backboneStart;
    private IAutoShape backboneEnd;

    private IAutoShape reactionShape;
    private IAutoShape hiddenReactionShape; // hidden behind the reactionShape
    private Map<Connector, IAutoShape> shapeMap = new HashMap<>();

    public PPTXReaction(Edge edge, Map<Long, PPTXNode> nodesMap) {
        this.edge = edge;
        this.nodesMap = nodesMap;
    }

    public void render(IShapeCollection shapes) {
        // It checks for all the shapes to be grouped and creates them in advance placing
        // the main shape in reactionShape and the rest in the shapeMap map
        IGroupShape reactionGroupShape = createReactionShape(shapes);

        if (hasInputs()) {
            if (onlyOneInputWithoutConnectors()) {
                IAutoShape input = nodesMap.get(edge.getInputs().get(0).getId()).getiAutoShape();
                connect(shapes, nodesMap.get(edge.getInputs().get(0).getId()), input, backboneStart, false); // do not render arrow
            } else {
                createConnectorsFromInputs(shapes);
            }
        }

        if (hasOutputs()) {
            if (onlyOneOutputWithoutConnectors()) {
                IAutoShape output = nodesMap.get(edge.getOutputs().get(0).getId()).getiAutoShape();
                connect(shapes, nodesMap.get(edge.getOutputs().get(0).getId()), output, backboneEnd, true); // render Arrow,
            } else {
                createConnectorsToOutputs(shapes);
            }
        }

        if (hasCatalyst()) createConnectorsToCatalyst(shapes);

        if (hasActivators()) createConnectorsToActivator(shapes);

        if (hasInhibitors()) createConnectorsToInhibitor(shapes);

        // Bring reaction shape to front after rendering I/O/C/A/Inhibitors
        shapes.reorder(shapes.size() - 1, reactionGroupShape);
    }

    private void createConnectorsFromInputs(IShapeCollection shapes) {
        for (ReactionPart reactionPart : edge.getInputs()) {
            PPTXNode input = nodesMap.get(reactionPart.getId());
            IAutoShape last = input.getiAutoShape();
            for (Connector connector : input.getConnectors()) {
                if (!isType(connector, "INPUT")) continue;
                createReactionAttributes(shapes, connector, input, last, backboneStart);
            }
        }
    }

    private void createConnectorsToOutputs(IShapeCollection shapes) {
        for (ReactionPart reactionPart : edge.getOutputs()) {
            PPTXNode output = nodesMap.get(reactionPart.getId());
            IAutoShape last = output.getiAutoShape();
            for (Connector connector : output.getConnectors()) {
                if (!isType(connector, "OUTPUT")) continue;
                boolean renderArrow = true;
                boolean hasSegments = false;
                for (int i = 1; i < connector.getSegments().size(); i++) {
                    Segment segment = connector.getSegments().get(i);
                    IAutoShape step = renderAuxiliaryShape(shapes, segment.getFrom()); //shapes.addAutoShape(ShapeType.Ellipse, segment.getFrom().getX().floatValue(), segment.getFrom().getY().floatValue(), 1f, 1f);

                    // As we are connecting from node to segment, then the first step should have the arrow.
                    if (i == 1) { // first step checks the anchor point
                        connect(shapes, output, last, step, renderArrow);
                    } else {
                        drawSegment(shapes, last, step);
                    }

                    last = step;
                    renderArrow = false;
                    hasSegments = true;
                }
                if (!hasSegments) {
                    // doesn't process segments, then take anchor point into consideration
                    connect(shapes, output, last, backboneEnd, true);
                } else {
                    drawSegment(shapes, last, backboneEnd);
                }
            }
        }
    }

    private void createConnectorsToCatalyst(IShapeCollection shapes) {
        for (ReactionPart reactionPart : edge.getCatalysts()) {
            PPTXNode catalyst = nodesMap.get(reactionPart.getId());
            IAutoShape last = catalyst.getiAutoShape();
            for (Connector connector : catalyst.getConnectors()) {
                if (!isType(connector, "CATALYST")) continue;
                createReactionAttributes(shapes, connector, catalyst, last, shapeMap.get(connector));
            }
        }
    }

    private void createConnectorsToActivator(IShapeCollection shapes) {
        for (ReactionPart reactionPart : edge.getActivators()) {
            PPTXNode activator = nodesMap.get(reactionPart.getId());
            IAutoShape last = activator.getiAutoShape();
            for (Connector connector : activator.getConnectors()) {
                if (!isType(connector, "ACTIVATOR")) continue;
                createReactionAttributes(shapes, connector, activator, last, shapeMap.get(connector)); // reactionShape
            }
        }
    }

    private void createConnectorsToInhibitor(IShapeCollection shapes) {
        for (ReactionPart reactionPart : edge.getInhibitors()) {
            PPTXNode inhibitor = nodesMap.get(reactionPart.getId());
            IAutoShape last = inhibitor.getiAutoShape();
            for (Connector connector : inhibitor.getConnectors()) {
                if (!isType(connector, "INHIBITOR")) continue;
                createReactionAttributes(shapes, connector, inhibitor, last, shapeMap.get(connector));
            }
        }
    }

    private void createReactionAttributes(IShapeCollection shapes, Connector connector, PPTXNode pptxNode, IAutoShape start, IAutoShape end) {
        boolean hasSegments = false;
        IAutoShape last = start; // not really need, just to make it is to understand
        for (int i = 0; i < connector.getSegments().size() - 1; i++) {
            Segment segment = connector.getSegments().get(i);
            IAutoShape step = renderAuxiliaryShape(shapes, segment.getTo());
            if (i == 0) { // first step checks the anchor point
                connect(shapes, pptxNode, last, step, false);
            } else {
                drawSegment(shapes, last, step);
            }
            last = step;
            hasSegments = true;
        }
        if (!hasSegments) {
            connect(shapes, pptxNode, last, end, false);
        } else {
            drawSegment(shapes, last, end);
        }
    }

    private IGroupShape createReactionShape(IShapeCollection shapes) {
        //Creates reaction shape
        IGroupShape groupShape = shapes.addGroupShape();
        Shape rShape = edge.getReactionShape();

        hiddenReactionShape = renderAuxiliaryShape(groupShape, edge.getPosition());
        reactionShape = renderShape(groupShape, rShape);

        createBackBone(shapes, rShape);

        if (hasCatalyst()) {
            for (ReactionPart reactionPart : edge.getCatalysts()) {
                PPTXNode catalyst = nodesMap.get(reactionPart.getId());
                for (Connector connector : catalyst.getConnectors()) {
                    if (!isType(connector, "CATALYST")) continue;
                    Shape shape = connector.getEndShape();
                    IAutoShape catalystAnchorPoint = renderAuxiliaryShape(groupShape, shape.getC());
                    IAutoShape cs = renderShape(groupShape, shape);
                    setShapeStyle(cs, new Stylesheet(1, LineStyle.Single, FillType.Solid, Color.BLACK, FillType.Solid, Color.WHITE));
                    shapeMap.put(connector, catalystAnchorPoint);
                }
            }
        }

        // NOTE 1: Is better not to draw the Activators shape and use the "end-arrow" in the last segment instead
        // NOTE 2: Could find any arrow shape for the Activators.
        if (hasActivators()) {
            for (ReactionPart reactionPart : edge.getActivators()) {
                PPTXNode activator = nodesMap.get(reactionPart.getId());
                for (Connector connector : activator.getConnectors()) {
                    if (!isType(connector, "ACTIVATOR")) continue;
                    Shape shape = connector.getEndShape();

                    IAutoShape centre = renderActivatorShape(shapes, groupShape, shape);
//                    IAutoShape a = PPTXShape.renderAuxiliaryShape(groupShape, shape.getA());
//                    IAutoShape b = PPTXShape.renderAuxiliaryShape(groupShape, shape.getB());
//                    IAutoShape c = PPTXShape.renderAuxiliaryShape(groupShape, shape.getC());

//                    drawSegment(shapes, a, b);
//                    drawSegment(shapes, b, c);
//                    drawSegment(shapes, c, a);

//                    float x = (shape.getA().getX().floatValue() + shape.getC().getX().floatValue()) / 2f;
//                    float y = (shape.getA().getY().floatValue() + shape.getC().getY().floatValue()) / 2f;
//                    IAutoShape centre = groupShape.getShapes().addAutoShape(ShapeType.Ellipse, x, y, 0.1f, 0.1f);
//                    centre.getLineFormat().getFillFormat().setFillType(FillType.NoFill);
//                    centre.getFillFormat().setFillType(FillType.NoFill);
                    shapeMap.put(connector, centre);
                }
            }
        }

        if (hasInhibitors()) {
            for (ReactionPart reactionPart : edge.getInhibitors()) {
                PPTXNode inhibitor = nodesMap.get(reactionPart.getId());
                for (Connector connector : inhibitor.getConnectors()) {
                    if (!isType(connector, "INHIBITOR")) continue;
                    Shape shape = connector.getEndShape();
                    // IMPORTANT: A line didn't work here. Has to be two connected shapes
                    IAutoShape a = renderAuxiliaryShape(groupShape, shape.getA());
                    IAutoShape b = renderAuxiliaryShape(groupShape, shape.getB());
                    IAutoShape centre = renderAuxiliaryShape(groupShape, shape.getC());
                    drawSegment(shapes, a, b);
                    shapeMap.put(connector, centre);
                }
            }
        }
        return groupShape;
    }

    /**
     * The backbone is a little bit special since it might contain a number of segments and the reaction shape
     * will be in the intersection of either of them.
     * <p>
     * The problem is that initially we don't know which one because the diagram has always been drawn in
     * raster and that was not a big problem. Now we have to figure out which connector connects to the
     * reaction shape, and that's what this method is doing (don't panic!)
     *
     * @param shapes the collection of shapes where the backbone has to be added
     * @param rShape the reaction shape previously created
     */
    private void createBackBone(IShapeCollection shapes, Shape rShape) {
        Coordinate start = edge.getSegments().get(0).getFrom();
        if (touches(rShape, start)) {
            backboneStart = hiddenReactionShape;
        } else {
            backboneStart = renderAuxiliaryShape(shapes, start);
        }
        IAutoShape last = backboneStart;
        for (int i = 1; i < edge.getSegments().size(); i++) { //IMPORTANT: It starts in "1" because "0" has been taken above
            Coordinate step = edge.getSegments().get(i).getFrom();
            if (touches(rShape, step)) {
                drawSegment(shapes, last, hiddenReactionShape);
                last = hiddenReactionShape;
            } else {
                IAutoShape backboneStep = renderAuxiliaryShape(shapes, step);
                drawSegment(shapes, last, backboneStep);
                last = backboneStep;
            }
        }
        backboneEnd = last; //The last one could either be an anchor point or the reactionShape itself, but that's not a problem at all!
    }



    private boolean onlyOneInputWithoutConnectors() {
        if (edge.getInputs().size() == 1) {
            List<Connector> connectors = nodesMap.get(edge.getInputs().get(0).getId()).getConnectors(edge.getId(), "INPUT");
            return connectors.isEmpty() || connectors.get(0).getSegments().isEmpty();
        }
        return false;
    }

    private boolean onlyOneOutputWithoutConnectors() {
        if (edge.getOutputs().size() == 1) {
            List<Connector> connectors = nodesMap.get(edge.getOutputs().get(0).getId()).getConnectors(edge.getId(), "OUTPUT");
            return connectors.isEmpty() || connectors.get(0).getSegments().isEmpty();
        }
        return false;
    }

    private boolean hasInputs() {
        return edge.getInputs() != null && !edge.getInputs().isEmpty();
    }

    private boolean isType(Connector connector, String type) {
        return Objects.equals(connector.getEdgeId(), edge.getId()) && Objects.equals(connector.getType(), type);
    }

    private boolean hasOutputs() {
        return edge.getOutputs() != null && !edge.getOutputs().isEmpty();
    }

    private boolean hasCatalyst() {
        return edge.getCatalysts() != null && !edge.getCatalysts().isEmpty();
    }

    private boolean hasActivators() {
        return edge.getActivators() != null && !edge.getActivators().isEmpty();
    }

    private boolean hasInhibitors() {
        return edge.getInhibitors() != null && !edge.getInhibitors().isEmpty();
    }

//    private int getAnchorPoint(IShape node, IShape backbone) {
//        boolean bottom = (node.getY() + node.getHeight()) <= (int) backbone.getY();
//        boolean top = (node.getY()) >= (int) backbone.getY();
//        boolean left = (int) backbone.getX() <= node.getX();
//        boolean right = (node.getX() + node.getHeight()) <= backbone.getX();
//
//        if (top) return 0;
//        if (left) return 1;
//        if (bottom) return 2;
//        if (right) return 3;
//
//        return 0;
//    }
}
