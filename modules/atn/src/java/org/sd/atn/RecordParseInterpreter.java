/*
    Copyright 2010 Semantic Discovery, Inc. (www.semanticdiscovery.com)

    This file is part of the Semantic Discovery Toolkit.

    The Semantic Discovery Toolkit is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    The Semantic Discovery Toolkit is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with The Semantic Discovery Toolkit.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.sd.atn;


import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sd.token.CategorizedToken;
import org.sd.token.Feature;
import org.sd.util.tree.NodePath;
import org.sd.util.tree.Tree;
import org.sd.xml.DomElement;
import org.sd.xml.DomNode;
import org.sd.xml.XmlLite;
import org.w3c.dom.NodeList;

/**
 * A generic parse interpreter.
 *
 * @author Spence Koehler
 */
public class RecordParseInterpreter implements AtnParseInterpreter {
  
  private String[] classifications;
  private InnerResources resources;
  private List<RecordTemplate> topTemplates;


  public RecordParseInterpreter(DomNode domNode, ResourceManager resourceManager) {
    this.resources = new InnerResources(resourceManager);
    this.topTemplates = new ArrayList<RecordTemplate>();

    // fill id2recordTemplate and topTemplates
    loadRecords(domNode, resources);

    final Set<String> topRecordTypes = new TreeSet<String>();
    for (RecordTemplate topTemplate : topTemplates) topRecordTypes.add(topTemplate.getId());
    this.classifications = topRecordTypes.toArray(new String[topRecordTypes.size()]);
  }

  /**
   * Get classifications offered by this interpreter.
   * <p>
   * Note that classifications are applied to parse-based tokens.
   */
  public String[] getClassifications() {
    return classifications;
  }

  /**
   * Get the interpretations for the parse or null.
   */
  public List<ParseInterpretation> getInterpretations(AtnParse parse) {
    List<ParseInterpretation> result = null;

    for (RecordTemplate topTemplate : topTemplates) {
      if (topTemplate.matches(parse)) {
        final ParseInterpretation interp = topTemplate.interpret(parse);
        if (interp != null) {
          if (result == null) result = new ArrayList<ParseInterpretation>();
          result.add(interp);
        }
      }
    }

    return result;
  }


  // fill id2recordTemplate and topTemplates
  private final void loadRecords(DomNode domNode, InnerResources resources) {
    final NodeList recordNodes = domNode.selectNodes("record");
    if (recordNodes != null) {
      final int numRecordNodes = recordNodes.getLength();
      for (int recordNodeNum = 0; recordNodeNum < numRecordNodes; ++recordNodeNum) {
        final DomElement recordNode = (DomElement)recordNodes.item(recordNodeNum);

        final RecordTemplate recordTemplate = new RecordTemplate(recordNode, resources);
        resources.id2recordTemplate.put(recordTemplate.getId(), recordTemplate);
        if (recordTemplate.isTop()) topTemplates.add(recordTemplate);
      }
    }
  }


  private static final class InnerResources {

    public final ResourceManager resourceManager;
    public final Map<String, RecordTemplate> id2recordTemplate;

    InnerResources(ResourceManager resourceManager) {
      this.resourceManager = resourceManager;
      this.id2recordTemplate = new HashMap<String, RecordTemplate>();
    }
  }


  private static final class RecordTemplate {

    private DomElement recordNode;
    private InnerResources resources;

    private String name;
    private String id;
    private boolean top;

    private NodeMatcher matcher;
    private List<FieldTemplate> fieldTemplates;
    private FieldTemplate nameOverride;

    RecordTemplate(DomElement recordNode, InnerResources resources) {
      this.recordNode = recordNode;
      this.resources = resources;

      this.name = recordNode.getAttributeValue("name");
      this.id = recordNode.getAttributeValue("type", this.name);

      final DomElement matchElement = (DomElement)recordNode.selectSingleNode("match");
      if (matchElement != null) {
        this.top = true;
        this.matcher = buildNodeMatcher(matchElement, resources);
      }

      this.nameOverride = null;
      this.fieldTemplates = new ArrayList<FieldTemplate>();
      final NodeList fieldList = recordNode.selectNodes("field");
      if (fieldList != null) {
        final int numFields = fieldList.getLength();
        for (int fieldNum = 0; fieldNum < numFields; ++fieldNum) {
          final DomElement fieldElement = (DomElement)fieldList.item(fieldNum);
          final FieldTemplate fieldTemplate = new FieldTemplate(fieldElement, resources);

          if (fieldTemplate.isNameOverride()) {
            this.nameOverride = fieldTemplate;
          }
          else {
            fieldTemplates.add(fieldTemplate);
          }
        }
      }
    }

    String getId() {
      return id;
    }

    boolean isTop() {
      return top;
    }

    String getNameOverride(AtnParse parse, Tree<String> parseNode, String fieldName) {
      String result = fieldName;

      if (nameOverride != null) {
        final List<Tree<String>> selected = nameOverride.select(parse, parseNode);
        if (selected != null && selected.size() > 0) {
          final Tree<String> selectedParseNode = selected.get(0);
          final String string = nameOverride.extractString(parse, selectedParseNode);
          if (string != null) result = string;
        }
      }

      return result;
    }

    boolean matches(AtnParse parse) {
      boolean result = true;

      if (matcher != null) {
        result = matcher.matches(parse);
      }

      return result;
    }

    ParseInterpretation interpret(AtnParse parse) {
      ParseInterpretation result = null;
      
      final Tree<String> parseTree = parse.getParseTree();
      final Tree<XmlLite.Data> interpTree = interpret(parse, parseTree, null, name);

      if (interpTree != null) {
        // add 'rule' attribute to interp (top)
        interpTree.getData().asTag().attributes.put("rule", parse.getStartRule().getRuleId());

        result = new ParseInterpretation(interpTree);
      }

      return result;
    }

    Tree<XmlLite.Data> interpret(AtnParse parse, Tree<String> parseNode,
                                 Tree<XmlLite.Data> parentNode, String fieldName) {

      fieldName = getNameOverride(parse, parseNode, fieldName);
      Tree<XmlLite.Data> result = XmlLite.createTagNode(fieldName);

      
      for (FieldTemplate fieldTemplate : fieldTemplates) {
        // select node(s) and extract value(s)...
        final List<Tree<String>> selectedNodes = fieldTemplate.select(parse, parseNode);

        if (selectedNodes != null && selectedNodes.size() > 0) {
          for (Tree<String> selectedNode : selectedNodes) {
            final List<Tree<XmlLite.Data>> values = fieldTemplate.extract(parse, selectedNode);
            if (values != null && values.size() > 0) {
              for (Tree<XmlLite.Data> value : values) {
                result.addChild(value);
              }
            }
          }
        }
      }

      if (result.numChildren() > 0) {
        if (!id.equals(fieldName)) {
          result.getData().asTag().attributes.put("type", id);
        }
        if (parentNode != null) parentNode.addChild(result);
      }
      else result = null;

      return result;
    }
  }


  private static final NodeMatcher buildNodeMatcher(DomElement matchElement, InnerResources resources) {
    //todo: decode attributes... for now, only RuleIdMatcher is being used...
    return new RuleIdMatcher(matchElement, resources);
  }

  private static interface NodeMatcher {
    public boolean matches(AtnParse parse);
  }

  private static final class RuleIdMatcher implements NodeMatcher {

    private Pattern pattern;

    RuleIdMatcher(DomElement matchElement, InnerResources resources) {
      //todo: decode attributes, for now, only doing regex 'matches'
      this.pattern = Pattern.compile(matchElement.getTextContent());
    }

    public boolean matches(AtnParse parse) {
      boolean result = false;
      final String ruleId = parse.getStartRule().getRuleId();
      if (ruleId != null && !"".equals(ruleId)) {
        final Matcher m = pattern.matcher(ruleId);
        result = m.matches();
      }
      return result;
    }
  }


  private static final class FieldTemplate {

    private String name;
    private boolean repeats;
    private boolean nameOverride;
    private NodeSelector selector;
    private NodeExtractor extractor;

    private NodeSelector nameSelector;
    private NodeExtractor nameExtractor;

    FieldTemplate(DomElement fieldElement, InnerResources resources) {
      this.name = fieldElement.getAttributeValue("name");
      this.repeats = fieldElement.getAttributeBoolean("repeats", false);
      this.nameOverride = "nameOverride".equals(fieldElement.getAttributeValue("type", null));

      // select
      final DomElement selectNode = (DomElement)fieldElement.selectSingleNode("select");
      this.selector = buildNodeSelector(selectNode, resources);

      // extract
      final DomElement extractNode = (DomElement)fieldElement.selectSingleNode("extract");
      this.extractor = buildNodeExtractor(this, extractNode, resources);
    }

    boolean isNameOverride() {
      return nameOverride;
    }

    String getName() {
      return name;
    }

    boolean repeats() {
      return repeats;
    }

    List<Tree<String>> select(AtnParse parse, Tree<String> parseNode) {
      return selector.select(parse, parseNode);
    }

    List<Tree<XmlLite.Data>> extract(AtnParse parse, Tree<String> parseNode) {
      return extractor.extract(parse, parseNode);
    }

    String extractString(AtnParse parse, Tree<String> parseNode) {
      return extractor.extractString(parse, parseNode);
    }
  }


  public static final NodeSelector buildNodeSelector(DomElement matchElement, InnerResources resources) {
    //todo: decode attributes... for now, only NodePathSelector is being used...
    return new NodePathSelector(matchElement, resources);
  }

  private static interface NodeSelector {
    public List<Tree<String>> select(AtnParse parse, Tree<String> parseTreeNode);
  }

  private static final class NodePathSelector implements NodeSelector {

    private NodePath<String> nodePath;
    private InnerResources resources;

    NodePathSelector(DomElement selectElement, InnerResources resources ) {
      //todo: decode attributes, none for now

      this.resources = resources;
      this.nodePath = new NodePath<String>(selectElement.getTextContent());
    }

    public List<Tree<String>> select(AtnParse parse, Tree<String> parseTreeNode) {
      return nodePath.apply(parseTreeNode);
    }
  }


  private static final NodeExtractor buildNodeExtractor(FieldTemplate fieldTemplate, DomElement extractElement, InnerResources resources) {
    NodeExtractor result = null;

    final String type = extractElement.getAttributeValue("type");
    final String data = extractElement.getTextContent();

    // <extract type='record'>relative</extract>
    // <extract type='attribute'>eventClass</extract>
    // <extract type='interp'>date</extract>
    // <extract type='text' />

    if ("record".equals(type)) {
      result = new RecordNodeExtractor(fieldTemplate, resources, data);
    }
    else if ("attribute".equals(type)) {
      result = new AttributeNodeExtractor(fieldTemplate, resources, data);
    }
    else if ("interp".equals(type)) {
      result = new InterpNodeExtractor(fieldTemplate, resources, data);
    }
    else if ("text".equals(type)) {
      result = new TextNodeExtractor(fieldTemplate, resources);
    }

    return result;
  }

  private static interface NodeExtractor {
    public List<Tree<XmlLite.Data>> extract(AtnParse parse, Tree<String> parseNode);
    public String extractString(AtnParse parse, Tree<String> parseNode);
  }

  private static abstract class AbstractNodeExtractor implements NodeExtractor {
    protected FieldTemplate fieldTemplate;
    protected InnerResources resources;

    AbstractNodeExtractor(FieldTemplate fieldTemplate, InnerResources resources) {
      this.fieldTemplate = fieldTemplate;
      this.resources = resources;
    }

    protected List<Tree<XmlLite.Data>> cleanup(Tree<XmlLite.Data> result, AtnParse parse, Tree<String> parseNode, boolean insertFieldNode) {
      List<Tree<XmlLite.Data>> retval = null;

      if (result != null) {
        retval = new ArrayList<Tree<XmlLite.Data>>();

        if (insertFieldNode) {
          final Tree<XmlLite.Data> fieldNode = XmlLite.createTagNode(fieldTemplate.getName());
          fieldNode.addChild(result);
          result = fieldNode;
        }

        retval.add(result);
      }

      return retval;
    }

    // if !repeats, insert a node designating ambiguity if necessary
    protected List<Tree<XmlLite.Data>> cleanup(List<Tree<XmlLite.Data>> result, AtnParse parse, Tree<String> parseNode, boolean insertFieldNode) {
      List<Tree<XmlLite.Data>> retval = result;

      if (retval != null) {

        final boolean isAmbiguous = !fieldTemplate.repeats() && retval != null && retval.size() > 1;

        if (insertFieldNode || isAmbiguous) {
          final Tree<XmlLite.Data> fieldNode = XmlLite.createTagNode(fieldTemplate.getName());
          if (isAmbiguous) fieldNode.getAttributes().put("ambiguous", "true");
          for (Tree<XmlLite.Data> child : result) fieldNode.addChild(child);

          retval = new ArrayList<Tree<XmlLite.Data>>();
          retval.add(fieldNode);
        }
      }

      return retval;
    }
  }

  private static final class RecordNodeExtractor extends AbstractNodeExtractor {

    private String recordId;

    RecordNodeExtractor(FieldTemplate fieldTemplate, InnerResources resources, String recordId) {
      super(fieldTemplate, resources);
      this.recordId = recordId;
    }

    public List<Tree<XmlLite.Data>> extract(AtnParse parse, Tree<String> parseNode) {
      List<Tree<XmlLite.Data>> result = null;

      final RecordTemplate recordTemplate = resources.id2recordTemplate.get(recordId);
      if (recordTemplate != null) {
        final Tree<XmlLite.Data> interpNode = recordTemplate.interpret(parse, parseNode, null, fieldTemplate.getName());
        result = super.cleanup(interpNode, parse, parseNode, false);
      }

      return result;
    }

    public String extractString(AtnParse parse, Tree<String> parseNode) {
      return null;
    }
  }

  private static final class AttributeNodeExtractor extends AbstractNodeExtractor {

    private String attribute;

    AttributeNodeExtractor(FieldTemplate fieldTemplate, InnerResources resources, String attribute) {
      super(fieldTemplate, resources);
      this.attribute = attribute;
    }

    public List<Tree<XmlLite.Data>> extract(AtnParse parse, Tree<String> parseNode) {
      List<Tree<XmlLite.Data>> result = null;

      //todo: parameterize featureClass (currently null) if/when needed
      final List<Feature> features = ParseInterpretationUtil.getAllTokenFeatures(parseNode, attribute, null);
      if (features != null) {
        result = new ArrayList<Tree<XmlLite.Data>>();
        for (Feature feature : features) {
          final Tree<XmlLite.Data> featureNode = XmlLite.createTextNode(feature.getValue().toString());
          result.add(featureNode);
        }
      }

      return cleanup(result, parse, parseNode, true);
    }

    public String extractString(AtnParse parse, Tree<String> parseNode) {
      String result = null;

      //todo: parameterize featureClass (currently null) if/when needed
      final Feature feature = ParseInterpretationUtil.getFirstTokenFeature(parseNode, attribute, null);
      if (feature != null) {
        result = feature.getValue().toString();
      }

      return result;
    }
  }

  private static final class InterpNodeExtractor extends AbstractNodeExtractor {

    private String classification;

    InterpNodeExtractor(FieldTemplate fieldTemplate, InnerResources resources, String classification) {
      super(fieldTemplate, resources);
      this.classification = classification;
    }

    public List<Tree<XmlLite.Data>> extract(AtnParse parse, Tree<String> parseNode) {
      List<Tree<XmlLite.Data>> result = null;

      final List<ParseInterpretation> interps = ParseInterpretationUtil.getInterpretations(parseNode, classification);
      if (interps != null) {

//todo: apply a disambiguation function to the interps here (e.g. fix 2-digit years) using full context of parse

        result = new ArrayList<Tree<XmlLite.Data>>();
        for (ParseInterpretation interp : interps) {
          result.add(interp.getInterpTree());
        }
      }

      return cleanup(result, parse, parseNode, false);
    }

    public String extractString(AtnParse parse, Tree<String> parseNode) {
      return null;
    }
  }

  private static final class TextNodeExtractor extends AbstractNodeExtractor {

    TextNodeExtractor(FieldTemplate fieldTemplate, InnerResources resources) {
      super(fieldTemplate, resources);
    }

    public List<Tree<XmlLite.Data>> extract(AtnParse parse, Tree<String> parseNode) {
      return super.cleanup(XmlLite.createTextNode(getText(parseNode)), parse, parseNode, true);
    }

    public String extractString(AtnParse parse, Tree<String> parseNode) {
      return getText(parseNode);
    }

    private String getText(Tree<String> parseNode) {
      String result = null;

      final CategorizedToken cToken = ParseInterpretationUtil.getCategorizedToken(parseNode);
      if (cToken != null) {
        result = cToken.token.getTextWithDelims();
      }

      if (result == null) {
        result = parseNode.getLeafText();
      }

      return result;
    }
  }
}
