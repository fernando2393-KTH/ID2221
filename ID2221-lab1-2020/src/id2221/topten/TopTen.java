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
import java.util.Collections;

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
			Map<String, String> map = transformXmlToMap(value.toString()); // Get tokens of entry
			if (map.get("Id") != null && !map.get("Id").equals("-1")) {
				double mapKey = Double.parseDouble(map.get("Reputation")); // Store the reputation as the integer part
				mapKey += Double.parseDouble(map.get("Id")) / Math.pow(10, map.get("Id").length()) + 
				(Math.pow(10, -(1 + map .get("Id").length()))); // Combine reputation and ID as unique key
				mapKey = Math.round(mapKey * Math.pow(10, map.get("Id").length() + 1)) / Math.pow(10, map.get("Id").length() + 1);
				repToRecordMap.put((mapKey), new Text (""));  // Write record in TreeMap
			}						
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void cleanup(Context context) throws IOException, InterruptedException {
		// Output our ten records to the reducers with a null key
		int cnt = 0;
		for (double key : repToRecordMap.descendingKeySet()) {
			context.write(NullWritable.get(), new Text (key + ""));
			cnt++;
			if (cnt == 10) {
				break;
			}
		}
	}
	}

	public static class TopTenReducer extends TableReducer<NullWritable, Text, NullWritable> {
		// Stores a map of user reputation to the record
		private TreeMap<Double, Text> repToRecordMap = new TreeMap<Double, Text>(Collections.reverseOrder());

	public void reduce(NullWritable key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
		try {
			for (Text val : values){
				repToRecordMap.put(Double.parseDouble(val.toString()), new Text ("")); // We give reputation as key so the treeMap orders according to reputation
			}
			int cnt = 0;
			for (Map.Entry<Double, Text> entry : repToRecordMap.entrySet()) {
				String doubleAsText = entry.getKey().toString();
				int reputation = Integer.parseInt(doubleAsText.split("\\.")[0]);  // Get reputation part
				String aux = doubleAsText.split("\\.")[1];  // Get decimal part 
				int id = Integer.parseInt(aux.substring(0, aux.length() - 1));  // Extract Id from decimal part
				Put insHBase = new Put(new Text(cnt + "").getBytes());
				insHBase.addColumn(Bytes.toBytes("info"), Bytes.toBytes("id"), new Text(id + "").getBytes());
				insHBase.addColumn(Bytes.toBytes("info"), Bytes.toBytes("reputation"), new Text(reputation + "").getBytes());
				context.write(null, insHBase);
				cnt++;
				if (cnt == 10) {
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	}

	public static void main(String[] args) throws Exception {
		Configuration conf = HBaseConfiguration.create();
		Job job = Job.getInstance(conf, "top-10 reputation");
		job.setJarByClass(TopTen.class);
		job.setMapperClass(TopTenMapper.class);
		job.setNumReduceTasks(1);
		job.setMapOutputKeyClass(NullWritable.class);
		job.setMapOutputValueClass(Text.class);
		FileInputFormat.addInputPath(job, new Path(args[0]));
		TableMapReduceUtil.initTableReducerJob("topten", TopTenReducer.class, job);
		System.exit(job.waitForCompletion(true) ? 0 : 1);

    }
}
