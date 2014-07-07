package com.nutomic.syncthingandroid.util;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.apache.http.conn.ssl.SSLSocketFactory;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.Random;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Provides direct access to the config.xml file in the file system.
 *
 * This class should only be used if the syncthing API is not available (usually during startup).
 */
public class ConfigXml {

	private static final String TAG = "ConfigXml";

	private File mConfigFile;

	private Document mConfig;

	public ConfigXml(File configFile) {
		mConfigFile = configFile;
		try {
			DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			mConfig = db.parse(configFile);
		} catch (SAXException e) {
			throw new RuntimeException("Failed to parse config file", e);
		} catch (ParserConfigurationException e) {
			throw new RuntimeException("Failed to parse config file", e);
		} catch (IOException e) {
			throw new RuntimeException("Failed to open config file", e);
		}
	}

	public String getWebGuiUrl() {
		boolean tlsEnabled = Boolean.parseBoolean(getGuiElement().getAttribute("tls"));
		return ((tlsEnabled) ? "https://" : "http://") +
				getGuiElement().getElementsByTagName("address").item(0).getTextContent();
	}

	public String getApiKey() {
		return getGuiElement().getElementsByTagName("apikey").item(0).getTextContent();
	}

	/**
	 * Updates the config file.
	 *
	 * Coming from 0.2.0 and earlier, globalAnnounceServer value "announce.syncthing.net:22025" is
	 * replaced with "194.126.249.5:22025" (as domain resolve is broken).
	 *
	 * Coming from 0.3.0 and earlier, the ignorePerms flag is set to true on every repository.
	 */
	@SuppressWarnings("SdCardPath")
	public void update() {
		Log.i(TAG, "Checking for needed config updates");
		boolean changed = false;
		Element options = (Element) mConfig.getDocumentElement()
				.getElementsByTagName("options").item(0);
		Element gui = (Element) mConfig.getDocumentElement()
				.getElementsByTagName("gui").item(0);

		// Create an API key if it does not exist.
		if (gui.getElementsByTagName("apikey").getLength() == 0) {
			Log.i(TAG, "Initializing API key with random string");
			char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
			StringBuilder sb = new StringBuilder();
			Random random = new Random();
			for (int i = 0; i < 20; i++) {
				sb.append(chars[random.nextInt(chars.length)]);
			}
			Element apiKey = mConfig.createElement("apikey");
			apiKey.setTextContent(sb.toString());
			gui.appendChild(apiKey);
			changed = true;
		}

		// Hardcode default globalAnnounceServer ip.
		Element globalAnnounceServer = (Element)
				options.getElementsByTagName("globalAnnounceServer").item(0);
		if (globalAnnounceServer.getTextContent().equals("announce.syncthing.net:22025")) {
			Log.i(TAG, "Replacing globalAnnounceServer host with ip");
			globalAnnounceServer.setTextContent("194.126.249.5:22025");
			changed = true;
		}

		NodeList repos = mConfig.getDocumentElement().getElementsByTagName("repository");
		for (int i = 0; i < repos.getLength(); i++) {
			Element r = (Element) repos.item(i);
			// Set ignorePerms attribute.
			if (!r.hasAttribute("ignorePerms") ||
					!Boolean.parseBoolean(r.getAttribute("ignorePerms"))) {
				Log.i(TAG, "Set 'ignorePerms' on repository " + r.getAttribute("id"));
				r.setAttribute("ignorePerms", Boolean.toString(true));
				changed = true;
			}

			// Replace /sdcard/ in repository path with proper path.
			String dir = r.getAttribute("directory");
			if (dir.startsWith("/sdcard")) {
				String newDir = dir.replace("/sdcard",
						Environment.getExternalStorageDirectory().getAbsolutePath());
				r.setAttribute("directory", newDir);
				changed = true;
			}
		}

		if (changed) {
			saveChanges();
		}
	}

	private Element getGuiElement() {
		return (Element) mConfig.getDocumentElement()
				.getElementsByTagName("gui").item(0);
	}

	/**
	 * Creates a repository for the default camera folder.
	 */
	public void createCameraRepo() {
		File cameraFolder =
				Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);

		Element cameraRepo = mConfig.createElement("repository");
		cameraRepo.setAttribute("id", "camera");
		cameraRepo.setAttribute("directory", cameraFolder.getAbsolutePath());
		cameraRepo.setAttribute("ro", "true");
		cameraRepo.setAttribute("ignorePerms", "true");
		mConfig.getDocumentElement().appendChild(cameraRepo);

		saveChanges();
	}

	/**
	 * Writes updated mConfig back to file.
	 */
	private void saveChanges() {
		try {
			Log.i(TAG, "Writing updated config back to file");
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource domSource = new DOMSource(mConfig);
			StreamResult streamResult = new StreamResult(mConfigFile);
			transformer.transform(domSource, streamResult);
		}
		catch (TransformerException e) {
			Log.w(TAG, "Failed to save updated config", e);
		}
	}

	public SSLSocketFactory createAdditionalCertsSSLSocketFactory(Context context) {
		try {
			X509V3CertificateGenerator v3CertGen = new X509V3CertificateGenerator();
			v3CertGen.setSerialNumber(BigInteger.valueOf(new SecureRandom().nextInt()));
			v3CertGen.setIssuerDN(new X509Principal("CN=syncthing, OU=None, O=None L=None, C=None"));
			v3CertGen.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30));
			v3CertGen.setNotAfter(new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 365*10)));
			v3CertGen.setSubjectDN(new X509Principal("CN=syncthing, OU=None, O=None L=None, C=None"));

			X509EncodedKeySpec spec =
					new X509EncodedKeySpec(keyBytes);
			KeyFactory kf = KeyFactory.getInstance("RSA");

			v3CertGen.setPublicKey(kf.generatePublic(spec));
			v3CertGen.setSignatureAlgorithm("MD5WithRSAEncryption");
			
			X509Certificate PKCertificate = v3CertGen.generateX509Certificate(KPair.getPrivate());

			KeyStore ks = KeyStore.getInstance("BKS");

			// the bks file we generated above
			FileInputStream fis =
					new FileInputStream(new File(context.getFilesDir(), "https-cert.pem"));
			try {
				ks.load(fis, null);
			} finally {
				fis.close();
			}

			return new SslSocketFactory(ks);

		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

}
