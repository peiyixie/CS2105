import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;

/*
Name: Xie Peiyi
Matric: A0141123B
*/

public class Bob {
	public static final int MAX_PACKET_SIZE = 512;

	public static void main(String[] args) throws Exception {
		int port = 0;
		try {
			port = Integer.parseInt(args[0]);
		} catch (Exception e) {
			System.out.println("Usage: java Bob <port>");
			System.exit(0);
		}
		receive(port);
	}

	public static void receive(int port) throws Exception {

		DatagramSocket receiverSocket = new DatagramSocket(port);
		byte[] receiverBuffer = new byte[MAX_PACKET_SIZE];
		String fileName = "undefined";
		int noOfPkts = 0;
		int receivedPktNo = 0;

		boolean receivedLast = false;
		BufferedOutputStream output = null;
		int seq = 0;
		int inOrderSeq = 0;
		boolean checksumFailed = false;
		
		InetAddress ackAddress = InetAddress.getByName("localHost");
		int ackPort = -1;
		while (!receivedLast) {

			DatagramPacket filePacket = new DatagramPacket(receiverBuffer, receiverBuffer.length);
			receiverSocket.receive(filePacket);
			ackAddress = filePacket.getAddress();
			ackPort = filePacket.getPort();
			byte[] receivedPkt = new byte[filePacket.getLength()];

			System.arraycopy(filePacket.getData(), filePacket.getOffset(), receivedPkt, 0, filePacket.getLength());

			System.out.println("received " + receivedPktNo + " pkt(s)");
			System.out.println("packet is  " + filePacket.getLength() + " bytes long");

			// compute checksum, if not ok, ignore
			checksumFailed = checksumHasFailed(receivedPkt);

			if (checksumFailed) {

				System.out.println("Packet corrupted, discard.");

			} else {
				System.out.println("Packet not corrupted.");
				// get sequence number
				ByteBuffer bufInt = ByteBuffer.wrap(receivedPkt, 8, 4);
				seq = bufInt.getInt();
				System.out.println("Packet Index: " + seq + ". Waiting for " + inOrderSeq);

				if (seq == 0) {
					// Get filename
					fileName = new String(receivedPkt, 12, 248).trim();
					System.out.println("Gotten file name: " + fileName);
					output = new BufferedOutputStream(new FileOutputStream(fileName, true));

					String noOfPktsStr = new String(receivedPkt, 300, 10).trim();
					noOfPkts = Integer.parseInt(noOfPktsStr);
					System.out.println("Number of packets to be received: " + noOfPkts);
					receivedPktNo++;
					inOrderSeq++;

				} else if (seq == inOrderSeq) {
					// if sequence is not 0, check if it's in order, if ok,
					// write to
					// file, increase inOrderSeq

					byte[] fileData = new byte[filePacket.getLength()];
					fileData = Arrays.copyOfRange(filePacket.getData(), 12, filePacket.getLength());
					output.write(fileData, 0, fileData.length);
					output.flush();
					inOrderSeq++;
					receivedPktNo++;
					System.out.println("Packet ok, written to file. Next packet shd be " + inOrderSeq);
					System.out.println("--------------------------------------------------");
					if (receivedPktNo == noOfPkts) {
						System.out.println("Last packet received");
						receivedLast = true;
					}

				} else {
					// if not in order, ignore
					System.out.println("Out of order packet, discard.");
				}
			}

			byte[] ackPktBytes = new byte[MAX_PACKET_SIZE];
			byte[] ackSeq = ByteConversion.intToByteArray(inOrderSeq);
			ackPktBytes = addDataToPacket(ackSeq, ackPktBytes, 8);
			ackPktBytes = addChecksumToPacket(ackPktBytes);

			DatagramPacket ackPacket = new DatagramPacket(ackPktBytes, ackPktBytes.length, filePacket.getAddress(),
					filePacket.getPort());
			receiverSocket.send(ackPacket);
			System.out.println("Sending ACK " + inOrderSeq);

		} // end of while loop

		output.close();
		int sendLastAck = 100;
		while (sendLastAck != 0) {

			byte[] ackPktBytes = new byte[MAX_PACKET_SIZE];
			byte[] ackSeq = ByteConversion.intToByteArray(inOrderSeq);
			ackPktBytes = addDataToPacket(ackSeq, ackPktBytes, 8);
			ackPktBytes = addChecksumToPacket(ackPktBytes);

			DatagramPacket ackPacket = new DatagramPacket(ackPktBytes, ackPktBytes.length, ackAddress,
					ackPort);
			receiverSocket.send(ackPacket);
			System.out.println("Sending ACK " + inOrderSeq);
			sendLastAck--;

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
			return false;
		} else {
			return true;
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