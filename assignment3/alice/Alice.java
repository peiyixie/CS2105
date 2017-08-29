
// Author: Xie Peiyi A0141123B

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SealedObject;

/**********************************************************************
 * This skeleton program is prepared for weak and average students. * * If you
 * are very strong in programming, DIY! * * Feel free to modify this program. *
 *********************************************************************/

// Alice knows Bob's public key
// Alice sends Bob session (AES) key
// Alice receives messages from Bob, decrypts and saves them to file

class Alice { // Alice is a TCP client

	private ObjectOutputStream toBob; // to send session key to Bob
	private ObjectInputStream fromBob; // to read encrypted messages from Bob
	private Crypto crypto; // object for encryption and decryption
	public static final String PUBLIC_KEY_FILE = "public.key";
	public static final String MESSAGE_FILE = "msgs.txt"; // file to store
															// messages

	public static void main(String[] args) throws UnknownHostException, IOException, InvalidKeyException,
			NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {

		// Check if the number of command line argument is 2
		if (args.length != 2) {
			System.err.println("Usage: java Alice BobIP BobPort");
			System.exit(1);
		}

		new Alice(args[0], args[1]);
	}

	// Constructor
	public Alice(String ipStr, String portStr) throws UnknownHostException, IOException, InvalidKeyException,
			NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {

		String serverIP = ipStr;
		int serverPort = Integer.parseInt(portStr);

		try {
			this.crypto = new Crypto();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Socket clientSocket = new Socket(serverIP, serverPort);
		System.out.println("Connected to " + serverIP + " at " + serverPort + "...");

		this.toBob = new ObjectOutputStream(clientSocket.getOutputStream());
		this.fromBob = new ObjectInputStream(clientSocket.getInputStream());
		// Send session key to Bob
		sendSessionKey();

		// Receive encrypted messages from Bob,
		// decrypt and save them to file
		try {
			receiveMessages();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// Send session key to Bob
	public void sendSessionKey() throws IOException {

		SealedObject sessionKeyToSend = null;
		try {
			sessionKeyToSend = this.crypto.getSessionKey();
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException
				| IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.toBob.writeObject(sessionKeyToSend);
		System.out.println("Sent Sealed Object");

	}

	// Receive messages one by one from Bob, decrypt and write to file
	public void receiveMessages() throws ClassNotFoundException, IOException, InvalidKeyException,
			NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
		PrintWriter output = new PrintWriter(new FileOutputStream(MESSAGE_FILE, true));

		SealedObject receivedObj = null;
		Boolean reachedEOF = false;
		try {
			while (!reachedEOF) {
				receivedObj = (SealedObject) this.fromBob.readObject();
				String plaintext = this.crypto.decryptMsg(receivedObj);
				output.println(plaintext);
				output.flush();
			}
		} catch (EOFException e) {
			reachedEOF = true;
			System.out.println("reached EOF");
		}
		output.close();
		// How to detect Bob has no more data to send?
	}

	/*****************/
	/** inner class **/
	/*****************/
	class Crypto {

		// Bob's public key, to be read from file
		private PublicKey pubKey;
		// Alice generates a new session key for each communication session
		private SecretKey sessionKey;
		// File that contains Bob' public key
		public static final String PUBLIC_KEY_FILE = "public.key";

		// Constructor
		public Crypto() throws NoSuchAlgorithmException {
			// Read Bob's public key from file
			readPublicKey();
			// Generate session key dynamically
			initSessionKey();
		}

		// Read Bob's public key from file
		public void readPublicKey() {
			// key is stored as an object and need to be read using
			// ObjectInputStream.
			// See how Bob read his private key as an example.

			try {
				ObjectInputStream ois = new ObjectInputStream(new FileInputStream(PUBLIC_KEY_FILE));
				this.pubKey = (PublicKey) ois.readObject();
				ois.close();
			} catch (IOException oie) {
				System.out.println("Error reading public key from file");
				System.exit(1);
			} catch (ClassNotFoundException cnfe) {
				System.out.println("Error: cannot typecast to class PublicKey");
				System.exit(1);
			}

			System.out.println("Public key read from file " + PUBLIC_KEY_FILE);

		}

		// Generate a session key
		public void initSessionKey() throws NoSuchAlgorithmException {
			// suggested AES key length is 128 bits
			KeyGenerator keyGen = KeyGenerator.getInstance("AES");
			keyGen.init(128);
			this.sessionKey = keyGen.generateKey();

		}

		// Seal session key with RSA public key in a SealedObject and return
		public SealedObject getSessionKey() throws NoSuchAlgorithmException, NoSuchPaddingException,
				IllegalBlockSizeException, IOException, BadPaddingException {
			SealedObject sessionKeyObj = null;

			// Alice must use the same RSA key/transformation as Bob specified
			Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");

			// RSA imposes size restriction on the object being encrypted (117
			// bytes).
			// Instead of sealing a Key object which is way over the size
			// restriction,
			// we shall encrypt AES key in its byte format (using getEncoded()
			// method).
			// this part UNSURE
			try {
				cipher.init(Cipher.ENCRYPT_MODE, this.pubKey);
			} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			sessionKeyObj = new SealedObject(this.sessionKey.getEncoded(), cipher);
			return sessionKeyObj;
		}

		// Decrypt and extract a message from SealedObject
		public String decryptMsg(SealedObject encryptedMsgObj)
				throws NoSuchAlgorithmException, NoSuchPaddingException, ClassNotFoundException,
				IllegalBlockSizeException, BadPaddingException, IOException, InvalidKeyException {

			String plainText = null;

			// Alice and Bob use the same AES key/transformation
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, this.sessionKey);
			plainText = (String) encryptedMsgObj.getObject(cipher);

			return plainText;
		}
	}
}