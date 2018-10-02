package com.paritosh.learning.hadoop;

import java.io.IOException;
import java.util.HashSet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;


public class SpatialJoin {

	
	public static String findGrid(int x, int y) {
		
		int gridX = x/1000;
		int gridY = y/1000;
		
		String grid = String.valueOf(gridX) + "," + String.valueOf(gridY);
		
		return grid;
	}
	

	/**
	 * Mapper which reports a point for a grid
	 * The whole plane is divided in grids of size 1000 * 1000
	 * @author paritoshgoel
	 *
	 */
	public static class GridMapperForPoints extends Mapper<Object, Text, Text, Text> {
		
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			int x = Integer.parseInt(value.toString().split(",")[0]);
			
			int y = Integer.parseInt(value.toString().split(",")[1]);
			
			String grid = findGrid(x , y);
			
			value = new Text("P,"+value.toString());
			context.write(new Text(grid),value);
			
		}
		
	}
	
	/**
	 * Mapper which reports a rectangle for a grid
	 * The whole plane is divided in grids of size 1000 * 1000
	 * @author paritoshgoel
	 *
	 */
	public static class GridMapperForRectangles extends Mapper<Object, Text, Text, Text> {
		
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String[] rec = value.toString().split(",");
			
			int bottomX = Integer.parseInt(rec[0]);
			int bottomY = Integer.parseInt(rec[1]);
			int height 	= Integer.parseInt(rec[2]);
			int width 	= Integer.parseInt(rec[3]);
			
			String grid1 = findGrid(bottomX, bottomY);
			String grid2 = findGrid(bottomX + width, bottomY);
			String grid3 = findGrid(bottomX + width, bottomY + height);
			String grid4 = findGrid(bottomX, bottomY + height);
			
			HashSet<String> setOfGrids = new HashSet<String>();
			setOfGrids.add(grid1);
			setOfGrids.add(grid2);
			setOfGrids.add(grid3);
			setOfGrids.add(grid4);
			
			for(String grid: setOfGrids) {
				Text rectangle = new Text("R," + value.toString());
				context.write(new Text(grid), rectangle);
			}
			
		}
		
	}
	
	/**
	 * Reducer which spatially joins points and rectangles
	 * @author paritoshgoel
	 *
	 */
	public static class GridReducer extends Reducer<Text,Text,Text,Text> {
		
		public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            String grid = key.toString();
            HashSet<String> setOfRectangles = new HashSet<String>();
            HashSet<String> setOfPoints = new HashSet<String>();
            
            // Prepare Set of Points and Rectangles
            for(Text value: values) {
            	String[] coordinates = value.toString().split(",");
            	if(coordinates[0].equals("R")) {
            		setOfRectangles.add(value.toString());
            	}
            	else
            		setOfPoints.add(values.toString());
            }
            
            //make a set of point objects in this grid to reduce computaion in loop 
            HashSet<Point> setOfPointObjects = new HashSet<Point>();
            for(String point: setOfPoints) {
        		String[] pointCoordinates = point.split(",");
        		int pointX = Integer.parseInt(pointCoordinates[1]);
        		int pointY = Integer.parseInt(pointCoordinates[2]);
        		setOfPointObjects.add(new Point(pointX,pointY));
        	}
            
            
            //For each rectangle , check if any point is inside the figure and write it to the output
            for(String rectangle: setOfRectangles) {
            	String[] rectangleCoordinates  = rectangle.split(",");
            	int bottomX = Integer.parseInt(rectangleCoordinates[1]);
            	int bottomY = Integer.parseInt(rectangleCoordinates[2]);
            	int rightX = bottomX + Integer.parseInt(rectangleCoordinates[4]);
            	int topY = bottomY + Integer.parseInt(rectangleCoordinates[5]);
            	
            	for(Point point: setOfPointObjects) {
            		
            		//if point lies in rectangle, write to output
            		if(point.x >= bottomX && point.x <=rightX) {
            			
            			if(point.y >=bottomY && point.y <= topY) {
            				
            				Text output = new Text(rectangle + " " + point.toString());
            				context.write(key, output);
            			}
            		}
            		
            	}
            }
            
        }
		
	}
	
	// execute the job 
	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		conf.set("mapred.textoutputformat.separator", ",");
		
		Job job = Job.getInstance(conf, "spatial join");
		job.setJarByClass(SpatialJoin.class);
		
		FileOutputFormat.setOutputPath(job, new Path(args[2]));
		
		job.setReducerClass(GridReducer.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		
		MultipleInputs.addInputPath(job, new Path(args[0]), TextInputFormat.class,GridMapperForPoints.class);
		MultipleInputs.addInputPath(job, new Path(args[0]), TextInputFormat.class,GridMapperForRectangles.class);
		
	}
}