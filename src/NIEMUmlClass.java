// James E. Cabral Jr, MTG Management Consutlants LLC jcabral@mtgmc.com
// Originsl version:  9/24/15
// Current version: July 15, 2016

// This ia plug_out that extends the BOUML UML tool with support for the National Information Exchange Model (NIEM) v3.2 defined at http://niem.gov.
// Specifically, it enables a UML Common Information Model (CIM), an abstract class mode, to be mapped into a
// UML Platform Speciic Model (PSM), the NIEM reference model, and a UML Platform Specific Model (PSM), NIEM XML Schema.
//
// NOTE: This plug_out requires that the BOUML project include a simple NIEM profile that provides the stereotypes required for mapping.

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.Map.Entry;
import javax.swing.*;
import javax.swing.filechooser.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
//import javax.xml.bind.*;            // JAXB
import javax.xml.parsers.*;         // DOM
import org.w3c.dom.*;               // DOM
import org.xml.sax.*;               // SAX
import com.opencsv.*;               // OpenCSV library

class NiemTools extends UmlClass
{

  private static HashMap<String,String> NiemElements = new HashMap<String,String>();
  private static HashMap<String,String> NiemTypes = new HashMap<String,String>();
  private static HashMap<String,List<String>> NiemElementsInType = new HashMap<String,List<String>>();
  private static HashMap<String,String> NiemNamespaces = new HashMap<String,String>();

  protected static final String niemStereotype = "niem-profile:niem";

  // NIEM mapping spreadsheet column headings, NIEM profile profile steroetype
  public static final String[][] map = {
 {"Model Class","",},
 {"Model Attribute","",},
 {"Model Multiplicity","",},
 {"Model Definition","",},
 {"NIEM XPath","XPath"},
 {"NIEM Type","Type"},
 {"NIEM Property (Representation)","Property"},
 {"NIEM Base Type","BaseType"},
 {"NIEM Multiplicity","Multiplicity"},
 {"Old XPath","OldXPath"},
 {"Old Multiplicity","OldMultiplicity"},
 {"NIEM Mapping Notes","Notes"}};

  // initializer
  public NiemTools(long id, String n)
  {
    super(id, n); inherited_opers = null;

    // initialize NIEM core and justice namespace mappings
    NiemNamespaces.put("http://release.niem.gov/niem/niem-core/3.0/","nc:");
    NiemNamespaces.put("http://release.niem.gov/niem/domains/jxdm/5.2","j:");

  }

  // generate NIEM mapping spreadhseet in CSV format
  // roundtripping is supported with importCsv()
  public static void exportCsv() throws IOException
  {
    fw = new FileWriter(directory + "/niem-mapping.csv");
    try
    {
      CSVWriter writer = new CSVWriter(fw);

      // Write header
      String[] nextLine = new String[map.length];
      for (int i=0;i<map.length;i++)
        nextLine[i]=map[i][0];
      writer.writeNext(nextLine);

      // Export NIEM Mappings for Classes
      for (int i=0; i<classes.size(); i++)
      {
        UmlItem c = (UmlItem) classes.elementAt(i);

        if (c.stereotype().equals(niemStereotype))
        {
          writer.writeNext(itemCsv(c));

          // Export NIEM Mapping for Attributes and Relations
          for (UmlItem ch : c.children())
            if (ch.stereotype().equals(niemStereotype))
            {
              nextLine = itemCsv(ch);
              if (nextLine != null)
                writer.writeNext(nextLine);
            }
        }
      }
      writer.close();

    } catch (FileNotFoundException e)
    {
      UmlCom.trace("File not found");
    } catch (IOException e)
    {
      UmlCom.trace("IO exception");
    }
  }

  // generate NIEM mapping spreadsheet in HTML format
  public static void exportHtml() throws IOException
  {
    start_file("niem_mapping", "NIEM Mapping", true);

    // Write header
    fw.write("<table><tr bgcolor=\"#f0f0f0\">");
    for (int i=0;i<map.length;i++)
      fw.write("<td>" + map[i][0] + "</td>");
    fw.write("</tr>\n");

    // Show NIEM Mappings for Classes
    for (int i=0; i<classes.size(); i++) {
      UmlItem c = (UmlItem) classes.elementAt(i);
      if (c.stereotype().equals(niemStereotype))
      {
        writeLineHtml(c);

        // Show NIEM Mapping for Attributes and Relations
        for (UmlItem ch : c.children())
        {
          if (ch.stereotype().equals(niemStereotype))
            writeLineHtml(ch);
        }
      }
    }
    fw.write("</table>\n");

    end_file();
  }

  // generate NIEM wantlist for import into Subset Schema Generator Tool (SSGT)
  public static void exportWantlist() throws IOException
  {
    // Export schema
    fw = new FileWriter(directory + "/wantlist.xml");
    fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    fw.write("<!-- NIEM Wantlist generated by BOUML2NIEM plug_out -->\n");
    fw.write("<w:WantList w:release=\"3.2\" w:product=\"NIEM\" w:nillableDefault=\"true\" ");
    for (Entry<String, String> entry : NiemNamespaces.entrySet()) {
      fw.write(" xmlns:" + entry.getValue() + "=\"" + entry.getKey() + "\"");
    }
    fw.write(">\n");

    String tn = niemStereotype+":" + map[5][1];
    String pn = niemStereotype+":" + map[6][1];
    String bn = niemStereotype+":" + map[7][1];
    String mn = niemStereotype+":" + map[8][1];
    String t, p, m, b, minoccurs, maxoccurs;
    Boolean invalid;

    // Iterate over all items with NIEM stereotype to export elements
    for (int i=0; i < all.size() ; i++) {
      UmlItem c = (UmlItem) all.elementAt(i);
      if (c.stereotype().equals(niemStereotype))
      {
        p = c.propertyValue(pn);
        if (!p.equals("")) {
          String[] pp = p.split(",");
          for (String ppp : pp)
          {
            ppp = ppp.trim();
            Matcher mat = Pattern.compile("\\((.*?)\\)").matcher(ppp);
            if (mat.find())
              ppp = mat.group(1);
            if (isNiemSchema(getSchema(ppp)))
                if (NiemElements.containsKey(ppp))
                  fw.write("<w:Element w:name=\"" + ppp + "\" w:isReference=\"false\" w:nillable=\"false\"/>\n");
                else
                  fw.write("<!--w:Element w:name=\"" + ppp + "\" w:isReference=\"false\" w:nillable=\"false\"/-->\n");
          }
        }
      }
    }

    // Iterate over all items with NIEM stereotype to export types
    for (int i=0; i < all.size() ; i++)
    {
      invalid = false;
      UmlItem c = (UmlItem) all.elementAt(i);
      if (c.stereotype().equals(niemStereotype))
      {
        t = c.propertyValue(tn);
        p = c.propertyValue(pn);
        b = c.propertyValue(bn);
        m = c.propertyValue(mn);
        if (p.equals(""))
        {
          String[] tt = t.split(",");
          for (String ttt : tt)
          {
            ttt = ttt.trim();
            // NIEM Type Mapping
            if (isNiemSchema(getSchema(ttt)))
            {

              // Export NIEM Types
              if (NiemTypes.containsKey(ttt))
                fw.write("<w:Type w:name=\"" + ttt + "\" w:isRequested=\"true\"/>\n");
              else
                fw.write("<!--w:Type w:name=\"" + ttt + "\" w:isRequested=\"true\"/-->\n");
            } else {

              String[] bb = b.split(",");
              for (String bbb : bb)
              {
                bbb = bbb.trim();
                if (isNiemSchema(getSchema(bbb)))
                {
                  if (NiemTypes.containsKey(bbb))
                    // Export NIEM Base Types for Non-NIEM Types
                    fw.write("<w:Type w:name=\"" + bbb + "\" w:isRequested=\"true\"/>\n");
                    else
                    fw.write("<!--w:Type w:name=\"" + bbb + "\" w:isRequested=\"true\"/-->\n");
                }
              }
            }
          }
        } else {

          // NIEM Element in Type Mapping
          if (isNiemSchema(getSchema(t)))
          {

            // Export NIEM Types
            if (NiemTypes.containsKey(t))
              fw.write("<w:Type w:name=\"" + t + "\" w:isRequested=\"true\">\n");
            else {
              fw.write("<!--w:Type w:name=\"" + t + "\" w:isRequested=\"true\"-->\n");
              invalid = true;
            }
            if (isNiemSchema(getSchema(p)))
            {

              // Export Element in Type
              if ((m.equals("")))
                minoccurs = maxoccurs = "1";
              else if (m.contains(",")) {
                String[] occurs = m.split(",");
                minoccurs = occurs[0];
                maxoccurs = occurs[1];
              } else
                minoccurs = maxoccurs = m;

              if (isNiemSchema(getSchema(t)))
              {
                String[] pp = p.split(",");
                for (String ppp : pp)
                {
                  ppp = ppp.trim();
                  Matcher mat = Pattern.compile("\\((.*?)\\)").matcher(ppp);
                  if (mat.find())
                    continue;
                  List<String> list = (List)(NiemElementsInType.get(t));
                  if ((!invalid) && ((list != null) && (list.contains(ppp))))
                    fw.write("\t<w:ElementInType w:name=\"" + ppp + "\" w:isReference=\"false\" w:minOccurs=\"" + minoccurs + "\" w:maxOccurs=\"" + maxoccurs + "\"/>\n");
                  else
                    fw.write("\t<!--w:ElementInType w:name=\"" + ppp + "\" w:isReference=\"false\" w:minOccurs=\"" + minoccurs + "\" w:maxOccurs=\"" + maxoccurs + "\"/-->\n");
                }
              }
              if (!invalid)
                fw.write("</w:Type>");
              else
                fw.write("<!--/w:Type-->");

           } else {
              if (!invalid)
                fw.write("</w:Type>");
              else
                fw.write("<!--/w:Type-->");
              if (isNiemSchema(getSchema(b)))
              {
                // Export NIEM Base Types for Non-NIEM Properties
                String[] bb = b.split(",");
                for (String bbb : bb)
                {
                  bbb = bbb.trim();
                  if (isNiemSchema(getSchema(bbb)))
                  {
                    if (NiemTypes.containsKey(bbb))
                      // Export NIEM Base Types for Non-NIEM Types
                      fw.write("<w:Type w:name=\"" + bbb + "\" w:isRequested=\"true\"/>\n");
                      else
                      fw.write("<!--w:Type w:name=\"" + bbb + "\" w:isRequested=\"true\"/-->\n");
                  }
                }
              }
            }
          }
        }
      }
    }

    fw.write("</w:WantList>");
    fw.close();

  }

  // extract namespace prefix from XML tag
  public static String getSchema(String typeName)
  {
    int i = typeName.indexOf(":");
    return (i>=0) ? typeName.substring(0,i) : "";

  }

  // import NIEM mapping spreadsheet in CSV format
  public static void importCsv() throws IOException
  {
    JFrame frame = new JFrame();
    JFileChooser fc = new JFileChooser(directory);
    fc.setFileFilter(new FileNameExtensionFilter("CSV file","csv"));
    fc.setDialogTitle("NIEM Mapping CSV file");

    if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION)
      throw new RuntimeException("abort");

    String filename = fc.getSelectedFile().getAbsolutePath();
    try {
      CSVReader reader = new CSVReader(new FileReader(filename));
      String[] nextLine;

      // Read header
      reader.readNext();

      // NIEM Read Mappings
      while ((nextLine = reader.readNext()) != null)
      {

        String className = nextLine[0].trim();
        String attributeName = nextLine[1].trim();
        for (int i=0;i<classes.size();i++) {
          UmlItem c = (UmlItem) classes.elementAt(i);
          if (c.stereotype().equals(niemStereotype) && (c.pretty_name().equals(className))) {

            if (attributeName.equals(""))
            {

              // Import NIEM Mapping to Class
              UmlCom.trace("Importing NIEM mapping for " + className);
              for (int p=4; p<map.length && p<nextLine.length; p++) {
                  c.set_PropertyValue(niemProperty(p),nextLine[p]);
              }
              break;

            } else
            {

              UmlItem[] ch = c.children();
              for (UmlItem item: ch)
              {
                if (item.stereotype().equals(niemStereotype) && (item.pretty_name().equals(attributeName))) {
                  // Import NIEM Mapping to Attribute
                  for (int p=4; p<map.length && p<nextLine.length; p++)
                  {
                    item.set_PropertyValue(niemProperty(p),nextLine[p]);
                  }
                  break;
                }
              }
            }
          }
        }
      }
      reader.close();

    } catch (FileNotFoundException e)
    {
      UmlCom.trace("File not found");
    } catch (IOException e)
    {
      UmlCom.trace("IO exception");
    }
  }

  // import NIEM refernce model into HashMaps to support validation of NIEM elements ant types
  public static void importSchema(String filename) throws IOException
  {
    //DOM parser
    try
    {
      // parse the schema
      DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
      Document doc = docBuilder.parse (new File(filename));
      // UmlCom.trace("File parsed");
      Node root, attr;
      NodeList list, anlist, cclist, exlist, slist, ellist;
      org.w3c.dom.Element s, e, an, cc, ex;
      String en, et, ed, bt;
      String prefix;
      List<String> enlist;

      // get namespaces
      root = doc.getDocumentElement();
      NamedNodeMap nslist = root.getAttributes();
      for(int i = 0 ; i < nslist.getLength(); i++)
      {
        attr = nslist.item(i);
        String aname = attr.getNodeName();
        String avalue = attr.getNodeValue();
        Integer index = aname.indexOf(":");

        if (index>0)
        {
          prefix = aname.substring(index+1);
          if ((!NiemNamespaces.containsKey(avalue)) && (!NiemNamespaces.containsValue(prefix)) && (!prefix.equals("schemaLocation")))
          {
            NiemNamespaces.put(avalue,prefix);
            // UmlCom.trace("Namespace: " + avalue + "(" + prefix + ")");
          }
        }
      }

      // get target Namespace
      attr = nslist.getNamedItem("targetNamespace");
      prefix = (attr != null) ? prefix = NiemNamespaces.get(attr.getNodeValue()) + ":" : "";

      // parse the elements
      list = root.getChildNodes();
      for(int i = 0 ; i < list.getLength(); i++)
      {

        // parse elements
        if (((Node)list.item(i)).getNodeName() == "xs:element")
        {
          e = (org.w3c.dom.Element)list.item(i);
          if (e.hasAttributes())
          {
            // prefix = e.getPrefix();
            en = e.getAttribute("name");
            et = e.getAttribute("type");
            ed = "";
            anlist = e.getElementsByTagName("xs:annotation");
            if (anlist.getLength()>0)
              ed = ((org.w3c.dom.Element)anlist.item(0)).getElementsByTagName("xs:documentation").item(0).getTextContent();

            if (!NiemElements.containsKey(prefix + en))
            {
              NiemElements.put(prefix + en,ed);
             // UmlCom.trace("Element " + en + " (" + et + ") - " + ed );
            }

            if (!NiemTypes.containsKey(et))
            {
              NiemTypes.put(et,ed);
             // UmlCom.trace("Element [" + prefix + en + "]");
           }
          }
        }

        // parse the types
        if (((Node)list.item(i)).getNodeName() == "xs:complexType")
        {
          e = (org.w3c.dom.Element)list.item(i);
          if (e.hasAttributes())
          {
            // prefix = e.getPrefix();
            en = e.getAttribute("name");
            ed = "";
            bt = "";
            et  = "";
            anlist = e.getElementsByTagName("xs:annotation");
            if (anlist.getLength()>0)
              ed = ((org.w3c.dom.Element)anlist.item(0)).getElementsByTagName("xs:documentation").item(0).getTextContent();
            cclist = e.getElementsByTagName("xs:complexContent");
            if (cclist.getLength()>0)
            {
              exlist = ((org.w3c.dom.Element)cclist.item(0)).getElementsByTagName("xs:extension");
              if (exlist.getLength()>0)
              {
                bt = ((org.w3c.dom.Element)exlist.item(0)).getAttribute("base");
                ellist = ((org.w3c.dom.Element)exlist.item(0)).getElementsByTagName("xs:element");
                for (int j=0; j < ellist.getLength(); j++)
                {
                  et = ((org.w3c.dom.Element)ellist.item(j)).getAttribute("ref");
                  // UmlCom.trace("Element " + et + " in type " + prefix + en);
                  enlist = (List)(NiemElementsInType.get(prefix + en));
                  if (enlist == null)
                  {
                    enlist = new ArrayList<String>();
                    enlist.add(et);
                    NiemElementsInType.put(prefix + en, enlist);
                  } else
                  {
                    enlist.add(et);
                  }
                }
              }
            } else
            {
              cclist = e.getElementsByTagName("xs:simpleContent");
              if (cclist.getLength()>0) {
                exlist = ((org.w3c.dom.Element)cclist.item(0)).getElementsByTagName("xs:extension");
                if (exlist.getLength()>0)
                  bt = ((org.w3c.dom.Element)exlist.item(0)).getAttribute("base");
              }
              // UmlCom.trace("Type " + en + " (" + bt + ") - " + ed );
              NiemTypes.put(prefix + en,ed);
              // UmlCom.trace("Type [" + prefix + en + "]");
            }

          }
        }
      }
    }
    catch (ParserConfigurationException e)
    {
      UmlCom.trace("ParserConfigurationException");
    }
    catch (SAXException e)
    {
      UmlCom.trace("SAXException");
    }
    catch (DOMException e)
    {
      UmlCom.trace("DOMException");
    }
    catch (IOException e)
    {
      UmlCom.trace("IOException");
    }
    catch (IllegalArgumentException e)
    {
      UmlCom.trace("IllegalArgumentException");
    }

  }

  // import NIEM refernce model into HashMaps to support validation of NIEM elements ant types
  public static void importSchemaDir() throws IOException
  {
    directory = UmlBasePackage.getProject().propertyValue("niem dir");

    // in java it is very complicated to select
    // a directory through a dialog, and the dialog
    // is very slow and ugly
    JFrame frame = new JFrame();
    JFileChooser fc = new JFileChooser(directory);

    fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    fc.setDialogTitle("Directory of the schema to be imported");

    if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION)
      throw new RuntimeException("abort");

    directory = fc.getSelectedFile().getAbsolutePath();
    UmlBasePackage.getProject().set_PropertyValue("niem dir",directory);

    // Walk directory
    Files.walkFileTree(

     FileSystems.getDefault().getPath(directory), new SimpleFileVisitor<Path>()
     {

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
      {
        String filename = file.toString();
        if (filename.endsWith(".xsd")) {
          // UmlCom.trace(filename);
          // List schema files
          importSchema(filename);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException
      {
        if (e == null)
        {
          return FileVisitResult.CONTINUE;
        } else
        {
          // directory iteration failed
          throw e;
        }
      }
     }
    );

    UmlCom.trace("Namespaces: " + NiemNamespaces.size());
    UmlCom.trace("Types: " + NiemTypes.size());
    UmlCom.trace("Elements: " + NiemElements.size());
  }

  // indicate whether an XML prefix matches a NIEM namespace
  public static boolean isNiemSchema(String schema)
  {
  //return Arrays.asList(niemSchema).contains(schema);
  return NiemNamespaces.containsValue(schema);
  }

  // return the NIEM steteotype associated with a column in the NIEM mapping spreadsheet
  public static String niemProperty(int p)
  {
  return niemStereotype + ":" + map[p][1];
  }

  // output a line of the NIEM mapping spreadhseet in HTML format
  public static void writeLineHtml(UmlItem item) throws IOException
  {

    // Export Class, Property and Multiplicity
    switch (item.kind().value())
    {
      case anItemKind._aClass:
        {
          fw.write("<tr bgcolor=\"#f0f0f0\"><td>");
          item.write();
          fw.write("</td><td>");
          fw.write("</td><td>");
        }
        break;
      case anItemKind._anAttribute:
      {
          fw.write("<tr><td>");
          item.parent().write();
          fw.write("</td><td>");
  	      item.write();
          fw.write("</td><td>");
          UmlAttribute a = (UmlAttribute) item;
          fw.write(a.multiplicity());
        }
        break;
      case anItemKind._aRelation:
        {
          UmlRelation rel = (UmlRelation) item;
          if ((rel.relationKind() == aRelationKind.aGeneralisation) ||
              (rel.relationKind() == aRelationKind.aRealization))
	    return;
          else {
            fw.write("<tr><td>");
            item.parent().write();
            fw.write("</td><td>");
  	    item.write();
            fw.write("</td><td>");
            fw.write(rel.multiplicity());
          }
        }
        break;
      default:
        return;
    }
    fw.write("</td><td>");

    // Export Description
    fw.write(item.description());
    fw.write("</td>");

    // Export NIEM Mapping
    int p;
    String oldValue, container;
    String[] column = new String[map.length];
    String extensionBGColor = "#ffd700";
    String defaultBGColor = "#ffffff";
    String invalidFGColor = "#ff0000";
    String changedFGColor = "#0000ff";
    String defaultFGColor = "#000000";
    String fgcolor, bgcolor;

    if (item.stereotype().equals(niemStereotype))
    {

      for (p=4;p<map.length;p++)
      {
        column[p]= (String)(item.propertyValue(niemProperty(p)));
        if (column[p] != null)
          column[p]=column[p].trim();
      }

      // export XPath
      String schema = getSchema(column[5]);
      Boolean extension = ((schema != null) && (!schema.equals("")) && (!isNiemSchema(schema)));
      bgcolor = (extension) ? extensionBGColor : defaultBGColor;
      fgcolor = (column[4].equals(column[9])) ? defaultFGColor : changedFGColor;
      fw.write(columnHtml(column[4], bgcolor, fgcolor));

      // export Type
      fgcolor = defaultFGColor;
      if (!extension)
      {
        String[] tt = column[5].split(",");
        for (String ttt : tt)
        {
          ttt = ttt.trim();
          if (!NiemTypes.containsKey(ttt))
            fgcolor = invalidFGColor;
        }
      }
      fw.write(columnHtml(column[5], bgcolor, fgcolor));

      // export Property
      fgcolor = defaultFGColor;
      if ((!column[5].equals("") && (!extension)))
      {
        String[] pp = column[6].split(",");
        for (String ppp : pp)
        {
          ppp = ppp.trim();
          Matcher mat = Pattern.compile("\\((.*?)\\)").matcher(ppp);
          if (!mat.find())
          {
            List<String> list = (List)(NiemElementsInType.get(column[5]));
            if (list == null)
              fgcolor = invalidFGColor;
            else if (!list.contains(ppp))
              fgcolor = invalidFGColor;
          }
        }
      }
      fw.write(columnHtml(column[6], bgcolor, fgcolor));

      // export BaseType
      fgcolor = defaultFGColor;
      if (!extension)
      {
        String[] bb = column[7].split(",");
        for (String bbb : bb)
        {
          bbb = bbb.trim();
          if (!NiemTypes.containsKey(bbb))
            fgcolor = invalidFGColor;
        }
      }
      fw.write(columnHtml(column[7], bgcolor, fgcolor));

      // export Multiplicity
      fgcolor = (column[8].equals(column[10])) ? defaultFGColor : changedFGColor;
      fw.write(columnHtml(column[8], bgcolor, fgcolor));

      // export Old XPath
      fgcolor = defaultFGColor;
      fw.write(columnHtml(column[9], bgcolor, fgcolor));

      // export Old Multiplicity
      fw.write(columnHtml(column[10], bgcolor, fgcolor));

      // export NIEM Mapping Notes
      fw.write(columnHtml(column[11], bgcolor, fgcolor));
    }

    fw.write("</tr>");
  }

  // output one column of the NIEM mapping spreadhsset in HTML format
//  protected static String columnHtml(String newValue, String oldValue, String container, String schema) throws IOException

  protected static String columnHtml(String value, String bgcolor, String fgcolor)
  {
    return "<td bgcolor=\"" + bgcolor +"\"><font color = \"" + fgcolor +"\">" + value + "</font></td>";
  }

  // reset NIEM mappings
  public static void deleteMapping()
  {
    Iterator<UmlItem> it = all.iterator();
    while (it.hasNext())
    {
      UmlItem item = it.next();
      if (item.stereotype().equals(niemStereotype))
        for (int p=4;p<map.length;p++)
          item.set_PropertyValue(niemProperty(p),"");
    }
  }

  // output one line of the NIEM mapping spreadsheet in CSV format
  public static String[] itemCsv(UmlItem item) throws IOException
  {
   String[] nextLine = new String[map.length];

    // Export Class and Property
    switch (item.kind().value())
    {
      case anItemKind._aClass:
        nextLine[0]=item.pretty_name();
	nextLine[1]="";
        break;
      default:
        nextLine[0]=item.parent().pretty_name();
        nextLine[1]=item.pretty_name();
        break;
    }

    // Export Multiplicity
    switch (item.kind().value())
    {
      case anItemKind._aClass:
        nextLine[2]="";
        break;
      case anItemKind._aRelation:
        UmlRelation rel = (UmlRelation) item;
        if ((rel.relationKind() == aRelationKind.aGeneralisation) ||
            (rel.relationKind() == aRelationKind.aRealization))
	  return null;
	nextLine[2]=rel.multiplicity();
        break;
      case anItemKind._anAttribute:
        UmlAttribute a = (UmlAttribute) item;
        nextLine[2]=a.multiplicity();
        break;
      default:
          return null;
    }

    // Export Description
    nextLine[3]=item.description();

    // Export NIEM Mapping
    if (item.stereotype().equals(niemStereotype))
    {
      String schema = getSchema((String)(item.propertyValue(niemStereotype+":Type")));
      for (int p=4;p<map.length;p++)
        nextLine[p]=item.propertyValue(niemProperty(p));
    }
    return nextLine;
  }

  // (re-)associate the NIEM stereotype with all properties in the CIM
  public static void resetStereotype()
  {
   Iterator it=all.iterator();
   while (it.hasNext())
   {
     UmlItem item = (UmlItem)it.next();
     if (item.stereotype().equals("niem:niem"))
     {
       item.set_Stereotype(niemStereotype);
       item.applyStereotype();
     }
   }
  }

}
