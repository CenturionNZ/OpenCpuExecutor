/*
 *   This file is part of PRCalcPlugin.
 *
 *   PRCalcPlugin is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Lesser General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   PRCalcPlugin is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with PPRCalcPlugin.  If not, see <http://www.gnu.org/licenses/>.
 *   
 *   Copyright 2013 dekarlab.de Theory. Solutions. Online Training.
 */

package de.dekarlab.pentahor;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.json.simple.JSONArray;
import org.json.simple.JSONValue;
import org.rosuda.JRI.RMainLoopCallbacks;

/**
 *
 * 
 * 
 */
public class OpenCpuRRunner {
	/**
	 * Path to R Script.
	 */
	private String filePath;
	/**
	 * Callback function for R engine.
	 */
	private RMainLoopCallbacks callback;
	/**
	 * Input variables.
	 */
	private PRVariable[] inputVars;
	/**
	 * Output variables.
	 */
	private PRVariable[] outputVars;


	private static OpenCpuRRunner instance;
	/**
	 * Is running?
	 */
	private boolean running = false;

	/**
	 * Constructor.
	 * 
	 * @param filePath
	 */
	private OpenCpuRRunner(String filePath, PRVariable[] inputVars,
			PRVariable[] outputVars, RCallback callback) {
		this.filePath = filePath;
		this.inputVars = inputVars;
		this.outputVars = outputVars;
		this.callback = callback;
	}

	/**
	 * Get instance.
	 * 
	 * @param filePath
	 * @param inputVars
	 * @param outputVars
	 * @return
	 * @throws Exception
	 */
	public static OpenCpuRRunner getInstance(String filePath, PRVariable[] inputVars,
			PRVariable[] outputVars) throws Exception {
		return getInstance(filePath, inputVars, outputVars, new RCallback(true));
	}

	/**
	 * Get instance.
	 * 
	 * @param filePath
	 * @param inputVars
	 * @param outputVars
	 * @param callback
	 * @return
	 * @throws Exception
	 */
	public static OpenCpuRRunner getInstance(String filePath, PRVariable[] inputVars,
			PRVariable[] outputVars, RCallback callback) throws Exception {
		if (instance == null) {
			instance = new OpenCpuRRunner(filePath, inputVars, outputVars, callback);
			String[] args = new String[] { "--vanilla" };
			//instance.initREngine(args);
		} else {
			instance.inputVars = inputVars;
			instance.outputVars = outputVars;
			instance.callback = callback;
			instance.filePath = filePath;
		}
		return instance;
	}

	/**
	 * Run evaluation.
	 */
	public synchronized void run() throws Exception {
		if (running) {
			throw new Exception("It is possible to have only one R session!");
		}
		try {
			running = true;
			if (callback instanceof RCallback) {
				((RCallback) callback).clearOutput();
			}			
			
			String rScript = GetRCodeFromFile(this.filePath);
			
			String result = CallOpenCpuApi(rScript, getInputVariablesArgs());

//			if (ret == null) {
//				throw new Exception(
//						"The R Script file should contain initialization of OUTPUT list to transfer values to PDI, for example, OUTPUT<-list(\"c\"=c)");
//			}
			
			setOutputVariables(result);
		} finally {
			running = false;
		}
	}

	/**
	 * Get output.
	 * 
	 * @return
	 */
	public String getOutput() {
		if (callback instanceof RCallback) {
			return ((RCallback) callback).getOutput();
		}
		return "empty";
	}

	/**
	 * Initialize variables.
	 */
	protected String getInputVariablesArgs() {	
		
		StringBuilder args = new StringBuilder();
		
		//{\"a\" : [1], \"b\": [2]}")
		args.append("{");

		for (PRVariable var : inputVars) {
			args.append("\"" + var.getrName() + "\": " + var.getValue() + ",");

		}
		
		String returnValue = args.toString();
		
		if (returnValue.endsWith(","))
			returnValue = returnValue.substring(0,  returnValue.length() - 1);
		

		returnValue = returnValue + "}";
		
		System.out.println(returnValue);
		
		return returnValue;
	}	

	/**
	 * Initialize column variables.
	 * 
	 * @param var
	 */
	protected void initColumnVariable(PRColumnVariable var) {
		switch (var.getType()) {
		case PRVariable.TYPE_BOOLEAN:
			Boolean[] valueFrom = var.getValueColumn().toArray(
					new Boolean[var.getValueColumn().size()]);
			boolean[] value = new boolean[valueFrom.length];
			for (int i = 0; i < value.length; i++) {
				value[i] = valueFrom[i];
			}
			//re.assign(var.getrName(), value);
			break;
		case PRVariable.TYPE_NUMBER:
			Double[] valueFromD = var.getValueColumn().toArray(
					new Double[var.getValueColumn().size()]);
			double[] valueD = new double[valueFromD.length];
			for (int i = 0; i < valueD.length; i++) {
				valueD[i] = valueFromD[i];
			}
			//re.assign(var.getrName(), valueD);
			break;
		case PRVariable.TYPE_STRING:
//			re.assign(
//					var.getrName(),
//					var.getValueColumn().toArray(
//							new String[var.getValueColumn().size()]));
			break;
		default:
		}

	}
	
	protected String CallOpenCpuApi(String rScript, String args)
	{
		String url = "http://10.0.0.5/ocpu/library/base/R/do.call/json";
		String result = "";
		try {
			URLConnection connection = new URL(url).openConnection();
			
			connection.setDoOutput(true);
			OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
			
			//writer.write("what=function(a, b){return(a*b)}&args={\"a\" : [1], \"b\": [2]}");		
			
			String postFormData = "what=" + rScript + "&args=" + args;

			System.out.println(postFormData);
			writer.write(postFormData);
			writer.flush();
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			StringBuilder content = new StringBuilder();
			String line;
			
			while ((line = reader.readLine()) != null)
			{
				content.append(line);
			}
			
			writer.close();
			reader.close();
			
			Object obj = JSONValue.parse(content.toString());
			JSONArray finalResult = (JSONArray)obj;
			
			result = finalResult.get(0).toString();
			System.out.println(result);
			
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
		return result;
	}

	/**
	 * Update output variables value.
	 */
	protected void setOutputVariables(String result) throws Exception {
		//RList list = ret.asList();
		for (PRVariable var : outputVars) {
			
			if (var == null) {
				throw new Exception("Var with name " + var.getrName()
						+ " is not found in OUTPUT.");
			}

			switch (var.getType()) {
			case PRVariable.TYPE_BOOLEAN:
//				if (varR.asBool().isTRUE()) {
//					var.setValue(true);
//				} else {
//					var.setValue(false);
//				}
				break;
			case PRVariable.TYPE_NUMBER:
				var.setValue(Double.parseDouble(result));
				break;
			case PRVariable.TYPE_STRING:
				var.setValue(result);
				break;
			default:
			}
		}
	}

	/**
	 * Evaluate file line by line.
	 * 
	 * @param fileName
	 * @return
	 * @throws Exception
	 */
	public String GetRCodeFromFile(String fileName) throws Exception {
		StringBuilder fileText = new StringBuilder();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(fileName));
			String line = br.readLine();
			while (line != null) {
				//re.eval(line);
				System.out.println(line);
				fileText.append(line);
				line = br.readLine();
			}
		} catch (Exception e) {
			throw e;
		} finally {
			if (br != null) {
				br.close();
			}
		}
		
		return fileText.toString();
	}

}
