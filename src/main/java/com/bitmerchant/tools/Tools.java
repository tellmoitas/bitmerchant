package com.bitmerchant.tools;

import static com.bitmerchant.wallet.LocalWallet.bitcoin;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.utils.MonetaryFormat;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;

import com.bitmerchant.wallet.LocalWallet;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class Tools {
	public static final Gson GSON = new Gson();
	public static final Gson GSON2 = new GsonBuilder().setPrettyPrinting().create();
	static final Logger log = LoggerFactory.getLogger(Tools.class);
	public static final DateTimeFormatter DTF2 = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss").
			withZone(DateTimeZone.UTC);
	public static final DateTimeFormatter DTF = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").
			withZone(DateTimeZone.UTC);


	public static void allowResponseHeaders(Request req, Response res) {
		String origin = req.headers("Origin");
		res.header("Access-Control-Allow-Credentials", "true");
		//		System.out.println("origin = " + origin);
		//		if (DataSources.ALLOW_ACCESS_ADDRESSES.contains(req.headers("Origin"))) {
		//			res.header("Access-Control-Allow-Origin", origin);
		//		}
		res.header("Access-Control-Allow-Origin", origin);

	}

	public static final Map<String, String> createMapFromAjaxPost(String reqBody) {
		//				log.info(reqBody);
		Map<String, String> postMap = new HashMap<String, String>();
		String[] split = reqBody.split("&");
		for (int i = 0; i < split.length; i++) {
			String[] keyValue = split[i].split("=");
			try {
				postMap.put(URLDecoder.decode(keyValue[0], "UTF-8"),URLDecoder.decode(keyValue[1], "UTF-8"));
			} catch (UnsupportedEncodingException |ArrayIndexOutOfBoundsException e) {
				e.printStackTrace();
				throw new NoSuchElementException(e.getMessage());
			}
		}

		log.info(GSON2.toJson(postMap));

		return postMap;

	}


	public static List<Map<String, String>> convertTransactionsToLOM(List<Transaction> transactions) {
		List<Map<String, String>> lom = new ArrayList<Map<String,String>>();

		for (Transaction cT : transactions) {
			Map<String, String> tMap = convertTransactionToMap(cT);
			lom.add(tMap);

		}

		return lom;
	}

	public static Map<String, String>convertTransactionToMap(Transaction tx) {
		Map<String, String> map = new LinkedHashMap<String, String>();

		Coin value = tx.getValue(bitcoin.wallet());
		Coin fee = tx.getFee();

		if (value.isPositive()) {
			map.put("message", "You received payment for an order");
			//			address = tx.getOutput(0).getAddressFromP2PKHScript(LocalWallet.params);
			//			address = tx.getOutput(0).getScriptPubKey().getFromAddress(LocalWallet.params);

			// TODO grab order number from SQL
			map.put("order", "asdf");


		} else if (value.isNegative()) {
			map.put("message", "You sent bitcoin to an external account");
			Address address = tx.getOutput(0).getAddressFromP2PKHScript(LocalWallet.params);
			//			address = tx.getOutput(0).getScriptPubKey().getFromAddress(LocalWallet.params);
			map.put("address", address.toString());

		}

		String dateStr = adjustUpdateTime(tx.getUpdateTime().getTime());

		//		String date = Tools.DTF2.print(dtStr);
		map.put("date", dateStr);

		if (fee != null) {

			map.put("fee", "<span class=\"text-muted\">-" + mBtcFormat(fee) + "</span>");


			// Subtract the fee from the net amount(in negatives)
			Coin amountBeforeFee = value.add(fee);
			map.put("amount", "<span class=\"text-danger\">" + mBtcFormat(amountBeforeFee) + "</span>");
		} 
		// If there was no fee, then you received btc
		else {
			map.put("amount", "<span class=\"text-success\"> +" + mBtcFormat(value) + "</span>");
		}


		String status = tx.getConfidence().getConfidenceType().name();

		// For now, if the value transferred is greater than 1 BTC, require 6 confirmations.
		// Otherwise, require only for it to be in the building state
		if (status.equals("BUILDING")) {
			int depth = tx.getConfidence().getDepthInBlocks();

			if (value.isGreaterThan(Coin.COIN)) {
				if (depth >=6) {
					status = "COMPLETED";
				} else {
					status = "PENDING";
				}
			} else {
				status = "COMPLETED";
			}
		} 

		map.put("status", status);
		map.put("depth",String.valueOf(tx.getConfidence().getDepthInBlocks()));

		return map;
	}

	public static String adjustUpdateTime(long time) {
		DateTime dt = new DateTime(time);//.minusHours(hours);
		String dateStr = dt.toString(DTF2);
		return dateStr;
	}

	public static String convertLOMtoJson(List<Map<String, String>> lom) {
		return Tools.GSON.toJson(lom);
	}

	public static String btcFormat(Coin c) {
		return MonetaryFormat.BTC.noCode().format(c).toString() + " BTC";
	}

	public static String mBtcFormat(Coin c) {
		return MonetaryFormat.MBTC.noCode().format(c).toString() + " mBTC";
	}


	public static void openWebpage(URI uri) {
		Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
		if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
			try {
				desktop.browse(uri);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void openWebpage(String urlString) {
		try {
			URL url = new URL(urlString);
			openWebpage(url.toURI());
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static String getTransactionOutputAddress(TransactionOutput txo) {
		return txo.getAddressFromP2PKHScript(LocalWallet.params).toString();
	}

	public static String getTransactionInputAddress(TransactionInput txi) {
		return getTransactionOutputAddress(txi.getConnectedOutput());
	}
	public static String getTransactionInfo(Transaction tx) {
		List<TransactionOutput> txos = tx.getOutputs();
		List<TransactionInput> txis = tx.getInputs();
		
		StringBuilder s = new StringBuilder();
		s.append("Inputs: \n");
		for (TransactionInput txi : txis) {
			s.append(getTransactionInputAddress(txi) + "\n");
		}

		s.append("Outputs: \n");
		for (TransactionOutput txo : txos) {
			s.append(getTransactionOutputAddress(txo) + "\n");
		}
		
		s.append("Hash: " + tx.getHashAsString() + "\n");
	
		
	
	

		return s.toString();
	}
	
	public static class TransactionJSON {
		String hash;
		String amount;
		
		public TransactionJSON(Transaction tx) {
			if (tx != null) {
			hash = tx.getHashAsString();
			amount = mBtcFormat(tx.getValue(bitcoin.wallet()));
			} else {
				hash = "none yet";
				amount = "none yet";
			}
		}
		
		public String json() {
			return GSON.toJson(this);
		}

		public String getHash() {
			return hash;
		}

		public String getAmount() {
			return amount;
		}
		
		
	}

	public static Integer cookieExpiration(Integer minutes) {
		return minutes*60;
	}
	
	public static final String httpGet(String url) {
		String res = "";
		try {
			URL externalURL = new URL(url);

			URLConnection yc = externalURL.openConnection();
			BufferedReader in = new BufferedReader(
					new InputStreamReader(
							yc.getInputStream()));
			String inputLine;

			while ((inputLine = in.readLine()) != null) 
				res+="\n" + inputLine;
			in.close();

			return res;
		} catch(IOException e) {}
		return res;
	}
	
	public static List<Map<String, String>> ListOfMapsPOJO(String json) {

		ObjectMapper mapper = new ObjectMapper();

		try {

			List<Map<String,String>> myObjects = mapper.readValue(json, 
					new TypeReference<ArrayList<LinkedHashMap<String,String>>>(){});

			return myObjects;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static Map<String, String> mapPOJO(String json) {

		ObjectMapper mapper = new ObjectMapper();

		try {

			Map<String,String> myObjects = mapper.readValue(json, 
					new TypeReference<LinkedHashMap<String,String>>(){});

			return myObjects;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static Map<String, Object> mapPOJO2(String json) {

		ObjectMapper mapper = new ObjectMapper();

		try {

			Map<String,Object> myObjects = mapper.readValue(json, 
					new TypeReference<LinkedHashMap<String,Object>>(){});

			return myObjects;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static JsonNode jsonToNode(String json) {

		ObjectMapper mapper = new ObjectMapper();

		try {

			JsonNode root = mapper.readTree(json);

			return root;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static void runSQLFile(Connection c,File sqlFile) throws SQLException, IOException {
		Statement stmt = null;
		stmt = c.createStatement();
		String sql =Files.toString(sqlFile, Charset.defaultCharset());
		stmt.executeUpdate(sql);
		stmt.close();
	}

}



