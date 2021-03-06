
package com.h2o.hive.udf;
import java.util.Arrays;
import org.apache.hadoop.hive.ql.udf.UDFType;
import org.apache.hadoop.hive.ql.exec.Description;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentLengthException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;

@UDFType(deterministic = true, stateful = false)
@Description(name="scoredata", value="_FUNC_(*) - Returns a score for the given row",
        extended="Example:\n"+"> SELECT scoredata(*) FROM target_data;")

class ScoreDataUDF extends GenericUDF {
  private PrimitiveObjectInspector[] inFieldOI;
  GBMPojo p = new GBMPojo();

  @Override
  public String getDisplayString(String[] args) {
    return "scoredata("+Arrays.asList(args)+").";
  }
  @Override
  public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
    // Basic argument count check
    // Expects one less argument than model used; results column is dropped
    if (args.length != GBMPojo.NAMES.length-1) {
      throw new UDFArgumentLengthException("Incorrect number of arguments." +
              "  scoredata() requires: "+Arrays.asList(GBMPojo.NAMES).remove(GBMPojo.NAMES.length-1)
              +", in the listed order.");
    }

    //Check input types
    inFieldOI = new PrimitiveObjectInspector[args.length];
    PrimitiveObjectInspector.PrimitiveCategory pCat;
    for (int i = 0; i < args.length; i++) {
      if (args[i].getCategory() != ObjectInspector.Category.PRIMITIVE)
        throw new UDFArgumentException("scoredata(...): Only takes primitive field types as parameters");
      pCat = ((PrimitiveObjectInspector) args[i]).getPrimitiveCategory();
      if (pCat != PrimitiveObjectInspector.PrimitiveCategory.STRING
              && pCat != PrimitiveObjectInspector.PrimitiveCategory.DOUBLE
              && pCat != PrimitiveObjectInspector.PrimitiveCategory.FLOAT
              && pCat != PrimitiveObjectInspector.PrimitiveCategory.LONG
              && pCat != PrimitiveObjectInspector.PrimitiveCategory.INT
              && pCat != PrimitiveObjectInspector.PrimitiveCategory.SHORT)
        throw new UDFArgumentException("scoredata(...): Cannot accept type: " + pCat.toString());
      inFieldOI[i] = (PrimitiveObjectInspector) args[i];
    }

    // the return type of our function is a float, so we provide the correct object inspector
    return PrimitiveObjectInspectorFactory.javaFloatObjectInspector;
  }

  @Override
  public Object evaluate(DeferredObject[] record) throws HiveException {
    // Expects one less argument than model used; results column is dropped
    if (record != null && record.length == GBMPojo.NAMES.length-1) {
      double[] data = new double[record.length];
      //Sadly, HIVE UDF doesn't currently make the field name available.
      //Thus this UDF must depend solely on the arguments maintaining the same
      // field order seen by the original H2O model creation.
      for (int i = 0; i < record.length; i++) {
        Object o = inFieldOI[i].getPrimitiveJavaObject(record[i].get());
        if (o instanceof java.lang.String) {
          // Hive wraps strings in double quotes, remove
          data[i] = p.mapEnum(i,((String) o).replace("\"",""));
          if (data[i] == -1)
            throw new UDFArgumentException("scoredata(...): Negative enum val from : "
                    + o +" for argument # "+i+".");
        } else if (o instanceof Double) {
          data[i] = ((Double)o);
        } else if (o instanceof Float) {
          data[i] = ((Float)o).doubleValue();
        } else if (o instanceof Long) {
          data[i] = ((Long)o).doubleValue();
        } else if (o instanceof Integer) {
          data[i] = ((Integer)o).doubleValue();
        } else if (o instanceof Short) {
          data[i] = ((Short)o).doubleValue();
        } else {
          throw new UDFArgumentException("scoredata(...): Cannot accept type: "
                  + o.getClass().toString()+" for argument # "+i+".");
        }
      }
      // get the predictions
      float[] preds = new float[p.getPredsSize()];
      p.predict(data, preds);
      return preds[0];
    } else {
      if (record == null) return null; //throw new UDFArgumentException("scoredata() received a NULL row.");
      else return null; // throw new UDFArgumentException("Incorrect number of arguments." +
      //"  scoredata() requires: "+Arrays.asList(GBMPojo.NAMES).remove(GBMPojo.NAMES.length-1)+", in order.");
    }
  }
}
