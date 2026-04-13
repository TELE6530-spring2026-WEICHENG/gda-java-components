/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * Copyright (c) 2020 - 2025 by Andrew D. King
 */

package programmingtheiot.common;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * A simple utility class that permits the loading, and in-memory
 * storage, of a given certificate that adheres to X.509 format.
 * 
 */
public class SimpleCertManagementUtil
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(SimpleCertManagementUtil.class.getSimpleName());
	
	private static final SimpleCertManagementUtil _Instance = new SimpleCertManagementUtil();
	
	// other options include SSL and TLSv1.3
	public static final String DEFAULT_SECURE_SOCKET_TYPE = "TLSv1.2";
	public static final String DEFAULT_CERTIFICATE_TYPE = "X.509";
	
	/**
	 * Returns the Singleton instance of {@link SimpleCertManagementUtil}.
	 * 
	 * @return CertManagementUtil
	 */
	public static final SimpleCertManagementUtil getInstance()
	{
		return _Instance;
	}
	
	
	// private var's
	
	
	// constructors
	
	/**
	 * Default (private).
	 * 
	 */
	private SimpleCertManagementUtil()
	{
		super();
	}
	
	
	// public methods
	
	/**
	 * Attempts to load the certificate contained in 'fileName'
	 * using the Java {@link KeyStore} and {@link TrustManagerFactory}
	 * functionality.
	 * <p>
	 * This will invoke the {@link #loadCertificate(String, String, String}
	 * method using the default certificate type of {@link #DEFAULT_CERTIFICATE_TYPE}
	 * and default socket type of {@link #DEFAULT_SECURE_SOCKET_TYPE}.
	 * <p>
	 * On success, the certificate will be loaded by the system, stored
	 * under a unique ID, and mapped to an {@link SSLSocketFactory}, which
	 * is returned to the caller.
	 * <p>
	 * On failure, an exception will be logged, and null will be returned.
	 * 
	 * @param fileName The certificate file name to load.
	 * @return SSLSocketFactory The socket factory initialized with
	 * the loaded certificate.
	 */
	public SSLSocketFactory loadCertificate(String fileName)
	{
		return loadCertificate(fileName, DEFAULT_CERTIFICATE_TYPE, DEFAULT_SECURE_SOCKET_TYPE);
	}

	/**
	 * Builds an {@link SSLSocketFactory} configured for mutual TLS (mTLS).
	 * <p>
	 * Required by cloud brokers such as AWS IoT Core which reject any client
	 * that does not present its own X.509 certificate during the TLS handshake.
	 * The returned factory combines:
	 * <ul>
	 *   <li>A TrustManager built from {@code caFileName} (the server / root CA).</li>
	 *   <li>A KeyManager built from {@code clientCertFileName} (the device's
	 *       certificate chain) and {@code clientKeyFileName} (its private key).</li>
	 * </ul>
	 * The private key file may be in either PKCS#1 ({@code BEGIN RSA PRIVATE KEY})
	 * or PKCS#8 ({@code BEGIN PRIVATE KEY}) PEM form. PKCS#1 keys are rewrapped
	 * into PKCS#8 in-memory so that {@link KeyFactory} can consume them without
	 * any third-party crypto dependency.
	 *
	 * @param caFileName         Path to the server / root CA PEM file.
	 * @param clientCertFileName Path to the client certificate PEM file.
	 * @param clientKeyFileName  Path to the client private key PEM file.
	 * @return Initialized {@link SSLSocketFactory}, or {@code null} on failure.
	 */
	public SSLSocketFactory loadMutualTlsSocketFactory(
		String caFileName,
		String clientCertFileName,
		String clientKeyFileName)
	{
		if (! isValid(caFileName)) {
			_Logger.warning("CA cert file not found: " + caFileName);
			return null;
		}
		if (! isValid(clientCertFileName)) {
			_Logger.warning("Client cert file not found: " + clientCertFileName);
			return null;
		}
		if (! isValid(clientKeyFileName)) {
			_Logger.warning("Client private key file not found: " + clientKeyFileName);
			return null;
		}

		try {
			// --- trust store (CA) ---
			KeyStore trustStore = importCertificate(caFileName, DEFAULT_CERTIFICATE_TYPE);

			TrustManagerFactory tmf =
				TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(trustStore);

			// --- key store (client cert chain + private key) ---
			CertificateFactory cf = CertificateFactory.getInstance(DEFAULT_CERTIFICATE_TYPE);

			Collection<? extends Certificate> clientCerts;
			try (FileInputStream fis = new FileInputStream(clientCertFileName)) {
				clientCerts = cf.generateCertificates(fis);
			}

			if (clientCerts == null || clientCerts.isEmpty()) {
				_Logger.severe("No certificates found in client cert file: " + clientCertFileName);
				return null;
			}

			Certificate[] chain = clientCerts.toArray(new Certificate[0]);
			PrivateKey privateKey = loadPrivateKey(clientKeyFileName);

			char[] password = new char[0];
			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(null, password);
			keyStore.setKeyEntry("client", privateKey, password, chain);

			KeyManagerFactory kmf =
				KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(keyStore, password);

			SSLContext sslContext = SSLContext.getInstance(DEFAULT_SECURE_SOCKET_TYPE);
			sslContext.init(
				kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

			_Logger.info(
				"mTLS socket factory initialized. CA: " + caFileName
				+ ", clientCert: " + clientCertFileName);

			return sslContext.getSocketFactory();
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to initialize mTLS socket factory.", e);
			return null;
		}
	}

	/**
	 * Loads an RSA private key from a PEM file. Accepts both PKCS#8 and PKCS#1
	 * forms — PKCS#1 is silently rewrapped into PKCS#8 so the standard
	 * {@link KeyFactory} can parse it.
	 */
	private PrivateKey loadPrivateKey(String fileName) throws Exception
	{
		String pem = new String(Files.readAllBytes(Paths.get(fileName)));
		boolean isPkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");

		byte[] der = pemToDer(pem);

		if (isPkcs1) {
			der = wrapPkcs1AsPkcs8(der);
		}

		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(der);
		return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
	}

	/**
	 * Strips PEM armor and base64-decodes the body to DER bytes.
	 */
	private byte[] pemToDer(String pem)
	{
		StringBuilder sb = new StringBuilder();

		for (String line : pem.split("\\r?\\n")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("-----")) {
				continue;
			}
			sb.append(trimmed);
		}

		return Base64.getDecoder().decode(sb.toString());
	}

	/**
	 * Wraps raw PKCS#1 RSA private key DER bytes into a PKCS#8 PrivateKeyInfo
	 * structure so that {@link KeyFactory} can consume it without BouncyCastle.
	 * <p>
	 * PKCS#8 layout:
	 * <pre>
	 *   SEQUENCE {
	 *     INTEGER 0                  -- version
	 *     SEQUENCE {                 -- algorithm identifier
	 *       OID 1.2.840.113549.1.1.1 -- rsaEncryption
	 *       NULL
	 *     }
	 *     OCTET STRING { pkcs1Bytes }
	 *   }
	 * </pre>
	 */
	private byte[] wrapPkcs1AsPkcs8(byte[] pkcs1)
	{
		byte[] version = new byte[] { 0x02, 0x01, 0x00 };
		byte[] algId = new byte[] {
			0x30, 0x0d,
			0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
			0x05, 0x00
		};
		byte[] octetHeader = buildDerTagLength((byte) 0x04, pkcs1.length);

		int innerLen = version.length + algId.length + octetHeader.length + pkcs1.length;
		byte[] seqHeader = buildDerTagLength((byte) 0x30, innerLen);

		byte[] out = new byte[seqHeader.length + innerLen];
		int pos = 0;

		System.arraycopy(seqHeader, 0, out, pos, seqHeader.length);
		pos += seqHeader.length;
		System.arraycopy(version, 0, out, pos, version.length);
		pos += version.length;
		System.arraycopy(algId, 0, out, pos, algId.length);
		pos += algId.length;
		System.arraycopy(octetHeader, 0, out, pos, octetHeader.length);
		pos += octetHeader.length;
		System.arraycopy(pkcs1, 0, out, pos, pkcs1.length);

		return out;
	}

	/**
	 * Encodes an ASN.1 DER tag + length prefix. Supports short form and
	 * long form up to 3 length bytes (sufficient for any RSA private key).
	 */
	private byte[] buildDerTagLength(byte tag, int length)
	{
		if (length < 0x80) {
			return new byte[] { tag, (byte) length };
		}
		if (length <= 0xff) {
			return new byte[] { tag, (byte) 0x81, (byte) length };
		}
		if (length <= 0xffff) {
			return new byte[] { tag, (byte) 0x82, (byte) (length >> 8), (byte) (length & 0xff) };
		}
		return new byte[] {
			tag, (byte) 0x83,
			(byte) (length >> 16), (byte) ((length >> 8) & 0xff), (byte) (length & 0xff)
		};
	}
	
	
	// private methods
	
	/**
	 * Attempts to load the certificate contained in 'fileName'
	 * using the Java {@link KeyStore} and {@link TrustManagerFactory}
	 * functionality.
	 * <p>
	 * On success, the certificate will be loaded by the system, stored
	 * under a unique ID, and mapped to an {@link SSLSocketFactory}, which
	 * is returned to the caller.
	 * <p>
	 * On failure, an exception will be logged, and null will be returned.
	 * 
	 * @param serverCrtFileName The CA / server certificate file name to load.
	 * @param certType The certificate type to load (e.g. "X.509"). If
	 * null or otherwise invalid (e.g. empty), will use the default
	 * {@link #DEFAULT_CERTIFICATE_TYPE}.
	 * @param socksType The socket type to initialize (e.g. "SSL"). If
	 * null or otherwise invalid (e.g. empty), will use the default
	 * {@link #DEFAULT_SECURE_SOCKET_TYPE}.
	 * @return SSLSocketFactory The socket factory initialized with
	 * the loaded certificate.
	 */
	private SSLSocketFactory loadCertificate(
		String serverCrtFileName,
		String certType,
		String socksType)
	{
		boolean hasServerCert = isValid(serverCrtFileName);
		
		if (! hasServerCert) {
			_Logger.warning("Server cert / CA not provided. Can't import certificates.");
			
			return null;
		}
		
		if (certType == null || certType.trim().length() == 0) {
			certType = DEFAULT_CERTIFICATE_TYPE;
			
			_Logger.warning(
				"Certificate type is null or empty. Using default: " + certType);
		}
		
		if (socksType == null || socksType.trim().length() == 0) {
			socksType = DEFAULT_SECURE_SOCKET_TYPE;
			
			_Logger.warning(
				"Socket type is null or empty. Using default: " + socksType);
		}
		
		// load server cert first
		SSLContext sslContext = null;
		
		try {
			sslContext = SSLContext.getInstance(socksType);
		} catch (Exception e) {
			_Logger.warning("Failed to initialize SSL context using protocol: " + socksType);
			
			return null;
		}
		
		try {
			_Logger.info(
				"Configuring " + socksType + " using " + certType);
			
			KeyStore keyStore = importCertificate(serverCrtFileName, certType);
			
			TrustManagerFactory trustManagerFactory =
				TrustManagerFactory.getInstance(
					TrustManagerFactory.getDefaultAlgorithm());
			
			trustManagerFactory.init(keyStore);
			sslContext.init(
				null, trustManagerFactory.getTrustManagers(), new SecureRandom());
			
			_Logger.info(
				certType + " certificate load and " + socksType +
				" socket init successful from file: " + serverCrtFileName);
				
			return sslContext.getSocketFactory();
		} catch (Exception e) {
			_Logger.log(
				Level.SEVERE,
				"Failed to initialize and load " + certType +
				" certificate(s) from file: " + serverCrtFileName,
				e);
		}
		
		return null;
	}
	
	/**
	 * Attempts to import the given certificate file and store as a uniquely
	 * named keystore reference (based on the filename and available bytes.
	 * 
	 * @param fileName The file name of the certificate to process.
	 * @param certType The certificate type to load (e.g. X.509).
	 * @return KeyStore A reference to the {@link KeyStore} containing
	 * the certificate.
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws IOException If an IO exception occurs, or if the file is
	 * available, but has 0 bytes to read.
	 */
	private KeyStore importCertificate(String fileName, String certType)
		throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException
	{
	    FileInputStream     fis = null;
	    KeyStore            ks  = null;
	    
	    try {
	    	fis = new FileInputStream(fileName);
	    	ks  = KeyStore.getInstance(KeyStore.getDefaultType());
	    	
	    	BufferedInputStream bis = new BufferedInputStream(fis);
	    	CertificateFactory  cf  = CertificateFactory.getInstance(certType);
	    	
	    	// just want to load the keystore with no parameters, so pass in 'null'
	    	ks.load(null);
	    	
	    	if (bis.available() == 0) {
	    		_Logger.warning(
	    			"No bytes available. Failed to import " + certType +
	    			" from file: " + fileName);
	    		
	    		throw new IOException(
	    			"File exists, but is empty. Can't import " + certType + " certificate.");
	    	} else {
	    		int    certCount   = 0;
	    		File   file        = new File(fileName);
	    		String entryPrefix = file.getName();
	    		
	    		// use while loop as we may have more than one cert in the file
	    		while (bis.available() > 0) {
	    			String      entryName = entryPrefix + "." + bis.available() + "." + ++certCount;
	    			Certificate cert      = cf.generateCertificate(bis);
	    			
	    			ks.setCertificateEntry(entryName, cert);
	    			
	    			_Logger.info(
	    				"Successfully imported " + certType +
	    				" certificate using entry name " + entryName +
	    				" from file: " + fileName);
	    		}
	    	}
	    } finally {
	    	// make sure the FileInputStream is closed in case of exception
	    	if (fis != null) {
	    		try {
	    			fis.close();
	    		} catch (Exception e) {
	    			_Logger.log(
	    				Level.WARNING,
	    				"Failed to close FileInputStream: " + fileName,
	    				e);
	    		} finally {
	    			fis = null;
	    		}
	    	}
	    }
	    
		return ks;		
	}
	
	/**
	 * Checks is the given file name is valid and available
	 * on the local file system.
	 * 
	 * @param fileName
	 * @return True on success; false otherwise.
	 */
	private boolean isValid(String fileName)
	{
		if (fileName != null) {
			if (new File(fileName).exists()) {
				_Logger.info(
					"Certificate / key file exists: " + fileName);
				
				return true;
			}
		} else {
			_Logger.info(
				"Certificate / key file name not loadable: " + fileName);
		}
		
		return false;
	}

}
