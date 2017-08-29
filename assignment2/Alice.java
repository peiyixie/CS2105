
/*
Name: Xie Peiyi
Matric: A0141123B
*/

import java.net.*;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

public class Alice {

	public static final int MAX_PACKET_SIZE = 512;
	public static final int MAX_DATA_SIZE = 500;

	public static void main(String[] args) throws InterruptedException {
		int N = 0;
		String fileToSend = null;
		int port = 0;
		String filenameAtBob = null;
		try {
			N = Integer.parseInt(args[0]);
			fileToSend = args[1];
			port = Integer.parseInt(args[2]);
			filenameAtBob = args[3];

		} catch (Exception e) {
			System.out.println("Usage: java Alice <N> <path/filename> <unreliNetPort> <rcvFileName>");
			System.exit(0);
		}
		sendFile(N, fileToSend, port, filenameAtBob);
	}

	public static void sendFile(int N, String fileToSend, int port, String filenameAtBob) throws InterruptedException {
		try {
			InetAddress bobAddress = InetAddress.getByName("localHost");
			DatagramSocket aliceSendSocket = new DatagramSocket();
			Path path = Paths.get(fileToSend);
			byte[] fileData = Files.readAllBytes(path);

			System.out.println("length of file is " + fileData.length + "bytes.");

			int lastPktNo = (int) Math.ceil((double) fileData.length / MAX_DATA_SIZE) + 1; // including
																							// first
																							// namePacket
			System.out.println("Last packet number will be " + lastPktNo);
			
			N = Math.min(N, lastPktNo);
			
			byte[] namePacket = new byte[MAX_PACKET_SIZE];
			byte[] firstSeqData = ByteConversion.intToByteArray(0);
			byte[] fileNameData = filenameAtBob.getBytes();
			byte[] noOfPktData = Integer.toString(lastPktNo).getBytes();
			namePacket = addDataToPacket(firstSeqData, namePacket, 8); // index
			namePacket = addDataToPacket(fileNameData, namePacket, 12); // name
			namePacket = addDataToPacket(noOfPktData, namePacket, 300); // number
																		// of
																		// packets
			namePacket = addChecksumToPacket(namePacket); // checksum

			DatagramPacket nameDatagramPacket = new DatagramPacket(namePacket, namePacket.length, bobAddress, port);

			int lastSent = 0, nextAck = 0;
			while (true) {

				while (lastSent - nextAck < N && lastSent < lastPktNo) {

					if (lastSent == 0) {
						aliceSendSocket.send(nameDatagramPacket);
						System.out.println("Filename packet sent. + ");
						lastSent++;

					} else {

						byte[] data = new byte[MAX_DATA_SIZE];

						data = Arrays.copyOfRange(fileData, (lastSent - 1) * MAX_DATA_SIZE,
								Math.min((lastSent - 1) * MAX_DATA_SIZE + MAX_DATA_SIZE, fileData.length));
						int sizeOfPkt = Math.min((lastSent - 1) * MAX_DATA_SIZE + MAX_DATA_SIZE, fileData.length)
								- (lastSent - 1) * MAX_DATA_SIZE + 12;

						byte[] filePacketBytes = new byte[sizeOfPkt];
						byte[] seqData = ByteConversion.intToByteArray(lastSent);

						filePacketBytes = addDataToPacket(seqData, filePacketBytes, 8);
						filePacketBytes = addDataToPacket(data, filePacketBytes, 12);
						filePacketBytes = addChecksumToPacket(filePacketBytes);

						DatagramPacket packet = new DatagramPacket(filePacketBytes, filePacketBytes.length, bobAddress,
								port);

						aliceSendSocket.send(packet);
						System.out.println("Sending packet with sequence number " + lastSent + " and size "
								+ filePacketBytes.length + " bytes");
						lastSent++;

					}
				}

				// edit this part, need to compute checksum also here.
				// Byte array for the ACK sent by the receiver
				byte[] ackBytes = new byte[MAX_PACKET_SIZE];

				// Creating packet for the ACK
				DatagramPacket ack = new DatagramPacket(ackBytes, ackBytes.length);

				try {
					aliceSendSocket.setSoTimeout(N * 150);

					aliceSendSocket.receive(ack);
					byte[] receivedAckPkt = new byte[ack.getLength()];
					boolean checksumFailed = false;

					System.arraycopy(ack.getData(), ack.getOffset(), receivedAckPkt, 0, ack.getLength());

					checksumFailed = checksumHasFailed(receivedAckPkt);

					if (checksumFailed) {

						System.out.println("Ack packet corrupted, discard.");

					} else {

						ByteBuffer bufInt = ByteBuffer.wrap(receivedAckPkt, 8, 4);
						int seqAck = bufInt.getInt();

						System.out.println("Received ACK" + seqAck);

						if (seqAck == lastPktNo) {
							break;
						}

						nextAck = Math.max(nextAck, seqAck);
					}

				} catch (SocketTimeoutException e) {

					// retransmit packet n and all subsequent packets in the
					// window
					System.out.println("timeout!!! !!!! !!!");
					
					for (int i = nextAck; i < lastSent; i++) {

						// GET DATA TO SEND
						byte[] data = new byte[MAX_DATA_SIZE];

						if (i == 0) {
							aliceSendSocket.send(nameDatagramPacket);

						} else {

							data = Arrays.copyOfRange(fileData, (i - 1) * MAX_DATA_SIZE,
									Math.min((i - 1) * MAX_DATA_SIZE + MAX_DATA_SIZE, fileData.length));
							int sizeOfPkt = Math.min((i - 1) * MAX_DATA_SIZE + MAX_DATA_SIZE, fileData.length)
									- (i - 1) * MAX_DATA_SIZE + 12;

							byte[] filePacketBytes = new byte[sizeOfPkt];
							byte[] seqData = ByteConversion.intToByteArray(i);

							filePacketBytes = addDataToPacket(seqData, filePacketBytes, 8);
							filePacketBytes = addDataToPacket(data, filePacketBytes, 12);
							filePacketBytes = addChecksumToPacket(filePacketBytes);

							DatagramPacket packet = new DatagramPacket(filePacketBytes, filePacketBytes.length,
									bobAddress, port);

							aliceSendSocket.send(packet);
							System.out.println("Re-transmitting packet with sequence number " + i + " and size "
									+ filePacketBytes.length + " bytes");
						}

					} // end for loop

				} // end catch

			} // End of send while loop

			aliceSendSocket.close();

		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Input/Output exception encountered.");
		}

	}

	private static byte[] addDataToPacket(byte[] data, byte[] packet, int pos) {
		System.arraycopy(data, 0, packet, pos, data.length);
		return packet;
	}

	private static byte[] addChecksumToPacket(byte[] packet) {
		CRC32 checksum = new CRC32();
		checksum.update(packet, 8, packet.length - 8);
		long checksumValue = checksum.getValue();
		byte[] checksumByteArray = ByteConversion.longToByteArray(checksumValue);
		System.arraycopy(checksumByteArray, 0, packet, 0, checksumByteArray.length);
		return packet;
	}

	private static boolean checksumHasFailed(byte[] packet) {
		CRC32 checksum = new CRC32();
		checksum.update(packet, 8, packet.length - 8);
		long checksumValue = checksum.getValue();

		byte[] checksumArray = new byte[8];
		for (int i = 0; i < 8; i++) {
			checksumArray[i] = packet[i];
		}
		long checksumInPacket = ByteConversion.byteArrayToLong(checksumArray);
		if (checksumInPacket == checksumValue) {
			return false; // checksum didn't fail
		} else {
			return true; // checksum failed
		}
	}

	private static class ByteConversion {
		private static ByteBuffer longBuffer = ByteBuffer.allocate(Long.BYTES);
		private static ByteBuffer intBuffer = ByteBuffer.allocate(Integer.BYTES);

		private static byte[] longToByteArray(long value) {
			longBuffer.clear();
			longBuffer.putLong(0, value);
			return longBuffer.array();
		}

		private static long byteArrayToLong(byte[] array) {
			longBuffer.clear();
			longBuffer.put(array, 0, array.length);
			longBuffer.flip();
			return longBuffer.getLong();
		}


		private static byte[] intToByteArray(int value) {
			intBuffer.clear();
			intBuffer.putInt(0, value);
			return intBuffer.array();
		}
	}
}