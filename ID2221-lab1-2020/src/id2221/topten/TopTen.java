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
		TreeMap<Integer, Text> repToRecordMap = new TreeMap<Integer, Text>();

	public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
	    try {
			// TODO: Iterate over the XML entries
			Map<String, String> map = transformXmlToMap((String) value); // Get tokens of entry
			if (map.get("Id") != null && !map.get("Id").equals("-1")) {
				repToRecordMap.put((Integer.parseInt(map.get("Id")), new Text (map.get("Reputation")));
			}						
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void cleanup(Context context) throws IOException, InterruptedException {
		// Output our ten records to the reducers with a null key
		// Modification of the comparator method in order to order by reputation
		List <Entry<Integer, Text>> list = new LinkedList<>(repToRecordMap.entrySet());
		Collections.sort(list, new Comparator<Map.Entry<Integer, Text>>() {
			@Override
			public int compare(Entry<Integer, Text> o1, Entry<Integer, Text> o2) {
				return Integer.parseInt(o1.getValue()).compareTo(Integer.parseInt(o2.getValue()));
			}
		});
		Map <Integer, Text> sorted = new LinkedHashMap<>(list.size());
		for (Entry<Integer, Text> entry : list) {
			sorted.put(entry.getKey(), entry.getValue());
		}
		
		List <Entry<Integer, Text>> entryList = new ArrayList <Map.Entry<Integer, Text>>(sorted.entrySet());
		for (int i = sorted.size() - 1; i > sorted.size() - 11; i--) {  // Iterate over the higher 10 rep values (last 10 elements)
			context.write(null, entryList.get(entryList.size()-i));
		}
	
		context.write(new Text(entry.getKey()), new IntWritable(entry.getValue()));
		
	}
	}

	public static class TopTenReducer extends TableReducer<NullWritable, Text, NullWritable> {
		// Stores a map of user reputation to the record
		private TreeMap<Integer, Text> repToRecordMap = new TreeMap<Integer, Text>();

	public void reduce(NullWritable key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
	    <FILL IN>
	}
	}

	public static void main(String[] args) throws Exception {
	<FILL IN>


    }
}
