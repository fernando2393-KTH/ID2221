package id2221.topten;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.mapreduce.TableReducer;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.filter.FirstKeyOnlyFilter;

// New imports
import java.lang.Math;

public class TopTen {
	// This helper function parses the stackoverflow into a Map for us.
	public static Map<String, String> transformXmlToMap(String xml) {
		Map<String, String> map = new HashMap<String, String>();
		try {
			String[] tokens = xml.trim().substring(5, xml.trim().length() - 3).split("\"");
			for (int i = 0; i < tokens.length - 1; i += 2) {
				String key = tokens[i].trim();
				String val = tokens[i + 1];
				map.put(key.substring(0, key.length() - 1), val);
			}
		} catch (StringIndexOutOfBoundsException e) {
			System.err.println(xml);
		}

		return map;
	}

	public static class TopTenMapper extends Mapper<Object, Text, NullWritable, Text> {
		// Stores a map of user reputation to the record
		TreeMap<Double, Text> repToRecordMap = new TreeMap<Double, Text>();

	public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
	    try {
			Map<String, String> map = transformXmlToMap((String) value); // Get tokens of entry
			if (map.get("Id") != null && !map.get("Id").equals("-1")) {
				double mapKey = Double.parseDouble(map.get("Reputation")) // Store the reputation as the integer part
				mapKey += Double.parseDouble(map.get("Id")) / Math.pow(10, map.get("Id").length()) + 
				(Math.pow(10, -(1 + map .get("Id").length()))); // Combine reputation and ID as unique key
				repToRecordMap.put((mapKey), new Text (""));  // Write record in TreeMap
			}						
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void cleanup(Context context) throws IOException, InterruptedException {
		// Output our ten records to the reducers with a null key
		int cnt = 0;
		for (Map.Entry<Double, Text> entry : repToRecordMap.entrySet()) {
			context.write(NullWritable.get(), new Text (entry.getKey()));  // TODO: Should we create an iterable and write at the end (out of the for loop)? or is it correct like this?
			cnt++;
			if (cnt == 10) {
				break;
			}
		}
	}
	}

	public static class TopTenReducer extends TableReducer<NullWritable, Text, NullWritable> {
		// Stores a map of user reputation to the record
		private TreeMap<Double, Text> repToRecordMap = new TreeMap<Double, Text>();

	public void reduce(NullWritable key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
		try {
			// TODO: Do we receive all the data from the cleanup in an iterable? How do we populate the new treemap?
			for (Text val : values){
				repToRecordMap.put(Double.parseDouble(val.get()), new Text ("")); // We give reputation as key so the treeMap orders according to reputation
			}
			int cnt = 0;
			for (Map.Entry<Double, Text> entry : repToRecordMap.entrySet()) {
				String doubleAsText = entry.getKey() + "";  // Convert double entry to String
				int reputation = Integer.parseInt(doubleAsText.split("\\.")[0]);  // Get reputation part
				String aux = doubleAsText.split("\\.")[1];  // Get decimal part 
				int id = Integer.parseInt(aux.substring(0, aux.length() - 1));  // Extract Id from decimal part
				// TODO: Write using Hbase
				Put insHBase = new Put(id.getBytes());
				insHBase.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("reputation"), Bytes.toBytes(reputation));
				context.write(null, insHBase);
				cnt++;
				if (cnt == 10) {
					break;
				}
			}
			context.write()
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	}

	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		Job job = Job.getInstance(conf, "top-10 reputation");
		job.setJarByClass(TopTen.class);
		job.setMapperClass(TopTenMapper.class);
		job.setCombinerClass(TopTenReducer.class);
		job.setReducerClass(TopTenReducer.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(IntWritable.class);
		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		System.exit(job.waitForCompletion(true) ? 0 : 1);

    }
}
