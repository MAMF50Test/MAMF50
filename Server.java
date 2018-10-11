package server;

import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;

import javax.net.*;
import javax.net.ssl.*;
import javax.security.cert.X509Certificate;

import users.*;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class Server implements Runnable {
	private ArrayList<ArrayList> section1, section2;
	private ArrayList<String> patient1, patient2;
	private HashMap<String, ArrayList<String>> patientJournals;
	private HashMap<String, ArrayList<ArrayList>> division;
	private ArrayList<String> journal;
	private ServerSocket serverSocket = null;
	private int numConnectedClients = 0;
	private BufferedWriter writer;
	private String[] split;

	public Server(ServerSocket ss) throws IOException {
		serverSocket = ss;
		newListener();
		patient1 = new ArrayList();
		patient2 = new ArrayList();
		section1 = new ArrayList();
		section2 = new ArrayList();
		journal = new ArrayList();

		patientJournals = new HashMap();
		division = new HashMap();
		writer = new BufferedWriter(new PrintWriter("Logger", "UTF-8"));

		fill();
		// FileWriter writer = new FileWriter("./Datasäkerhet/Logger");
	}

	public void run() {
		try {

			SSLSocket socket = (SSLSocket) serverSocket.accept();
			newListener();
			SSLSession session = socket.getSession();
			X509Certificate cert = (X509Certificate) session.getPeerCertificateChain()[0];
			String subject = cert.getSubjectDN().getName();

			String info[] = new String[] { subject.split("CN=")[1].split(",")[0], // PNbr
					subject.split("OU=")[1].split(",")[0], // Division
					subject.split("O=")[1].split(",")[0], // Usertype
					subject.split("L=")[1].split(",")[0], // Name
			};

			numConnectedClients++;
			System.out.println("client connected");
			System.out.println("client name (cert subject DN field): " + subject);
			System.out.println("issuer: " + cert.getIssuerDN().getName());
			System.out.println("serialno: " + cert.getSerialNumber().toString());
			System.out.println(numConnectedClients + " concurrent connection(s)\n");
			PrintWriter out = null;
			BufferedReader in = null;
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			// String clientMsg = null;

			// password implementation
			// String[] CNAndName = subject.split("=");
			User currentUser = null;
			switch (info[2].toString()) {
			case "Doctor":
				currentUser = new Doctor(info[3], info[0], info[1]);
				break;
			case "Nurse":
				currentUser = new Nurse(info[3], info[0], info[1]);
				break;
			case "Patient":
				currentUser = new Patient(info[3], info[0]);
				break;
			case "Agent":
				currentUser = new Agent(info[0]);
				break;
			}

			out.println("Authenticated Enter a command: ");
			out.flush();
			if ((currentUser != null)) {
				// fix commands
				String reply = "";
				do {
					reply = executeCommand(sendRequest("Enter a command: ", in, out), in, out, currentUser);
					logg(reply);
					// System.out.println("received '" + clientMsg +
					// "' from client");
					// System.out.print("sending '" + rev + "' to client...");
					System.out.println(reply);
					out.println(reply + "\nEnter a command: ");
					out.flush();
				} while (!reply.equalsIgnoreCase(""));
			} else {
				out.println("Bad Credentials. Closing connection ..");
				out.flush();
			}
			in.close();
			out.close();
			socket.close();
			writer.close();
			numConnectedClients--;
			System.out.println("client disconnected");
			System.out.println(numConnectedClients + " concurrent connection(s)\n");
		} catch (IOException e) {
			System.out.println("Client died: " + e.getMessage());
			e.printStackTrace();
			return;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private String executeCommand(String command, BufferedReader in, PrintWriter out, User currentUser)
			throws Exception {
		// command ="";
		logg(command);
		if (command.equals(null))
			return "No valid command entered, please try again!";
		else {
			if (command.contains("edit") || command.contains("remove") || command.contains("add")) {
				split = command.split("/");
				command = split[0];
			}
			switch (command.toLowerCase()) {
			case "help":
				return "The commands are: help, add, remove, read, edit. Please try again: ";
			case "add":
				if (!(currentUser instanceof Doctor))
					return "Unauthorized.";
				else
					return addPatient(in, out, currentUser);
			case "remove":
				if (!(currentUser instanceof Agent))
					return "Unauthorized";
				else
					return removeEntry(split, out, currentUser);
			case "read":
				return readJournal(in, out, currentUser);
			case "edit":
				if (!((currentUser instanceof Caretaker)))
					return "Unauthorized";
				else {
					return editJournal(split, out, currentUser);
				}

			default:
				return "No valid command entered, please try again!";
			}
		}
	}

	private String editJournal(String[] splitString, PrintWriter out, User currentUser) throws Exception {

		System.out.println(splitString[1]);
		// out.flush();
		patientJournals.get(splitString[1]).add(splitString[2]);
		int size = patientJournals.get(splitString[1]).size();
		// int entry = Integer.parseInt(sendRequest(
		// "Which entry do you want to remove?", in, out));

		return patientJournals.get(splitString[1]).get(size - 1) + " \n";
	}

	private String readJournal(BufferedReader in, PrintWriter out, User currentUser) throws Exception {
		StringBuilder entry = new StringBuilder("");

		if (currentUser instanceof Caretaker) {

			ArrayList<ArrayList> tempDivision = division.get(((Caretaker) currentUser).getDivision());

			for (ArrayList<String> i : tempDivision) {
				entry.append("New patient: ");
				for (String j : i) {
					entry.append("\n-----------\n" + j + "\n-----------\n");
					logg(currentUser.getName() + " has accessed " + entry);
				}
			}
			return entry.toString();
		} else if (currentUser instanceof Agent) {
			for (String key : patientJournals.keySet()) {
				entry.append("New Patient \n" + key + "\n");
				ArrayList<String> patient = patientJournals.get(key);
				for (String journalEntry : patient) {
					entry.append("----------- \n" + journalEntry + "\n-----------\n");
					logg(currentUser.getName() + " has accessed " + entry);
				}
			}

		} else {
			ArrayList<String> journal = patientJournals.get(currentUser.getPNbr());
			for (String s : journal) {
				entry.append("----------- \n" + s + "\n-----------\n");
				logg(currentUser.getName() + " has accessed " + entry);
			}
		}
		return entry.toString();
	}

	private String removeEntry(String[] splitString, PrintWriter out, User currentUser) throws Exception {

		journal = patientJournals.get(splitString[1]);
		return journal.remove(Integer.parseInt(splitString[2]) - 1) + " was removed.";
	}

	private String addPatient(BufferedReader in, PrintWriter out, User currentUser) {
		ArrayList patient0 = new ArrayList<String>();
		patient0.add(split[3]);
		division.get(split[2]).add(patient0);
		patientJournals.put(split[1], patient0);
		return "Patient was succesfully added to division " + split[2] + " \n";
	}

	private void newListener() {
		(new Thread(this)).start();
	} // calls run()

	private void fill() {
		patient1.add("Entry 1: Patient is cray-cray");
		patient1.add("Entry 2: Patient suspects he is a chihuauhua");
		patient2.add("Everything is fine, nothing to see here");
		patientJournals.put("940409-7116", patient1);
		patientJournals.put("340819-3984", patient2);
		section1.add(patient1);
		section2.add(patient2);
		division.put("1", section1);
		division.put("2", section2);
	}

	private String sendRequest(String request, BufferedReader in, PrintWriter out) throws Exception {
		System.out.print("sending '" + request + "' to client...");
		String clientAns = in.readLine();
		if (clientAns == null)
			throw new Exception("Client timed-out");
		System.out.println("Recieved answer: " + clientAns);
		return clientAns;
	}

	private static ServerSocketFactory getServerSocketFactory(String type) {
		if (type.equals("TLS")) {
			SSLServerSocketFactory ssf = null;
			try { // set up key manager to perform server authentication
				SSLContext ctx = SSLContext.getInstance("TLS");
				KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
				TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
				KeyStore ks = KeyStore.getInstance("JKS");
				KeyStore ts = KeyStore.getInstance("JKS");
				char[] password = "password".toCharArray();
				ks.load(new FileInputStream("./cert/server/Server/serverkeystore"), password); // keystore
				ts.load(new FileInputStream("./cert/server/Server/servertruststore"), password);
				// password
				// (storepass)
				// password
				// (storepass)
				kmf.init(ks, password); // certificate password (keypass)
				tmf.init(ts); // possible to use keystore as truststore here
				ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
				ssf = ctx.getServerSocketFactory();
				return ssf;
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			return ServerSocketFactory.getDefault();
		}
		return null;
	}

	public void logg(String in) throws IOException {
		writer.write(in, 0, in.length());
		writer.write("\n");
		writer.flush();

	}

	public static void main(String args[]) {
		System.out.println("\nServer Started\n");
		// fill();
		int port = 1994;
		String type = "TLS";
		try {
			ServerSocketFactory ssf = getServerSocketFactory(type);
			ServerSocket ss = ssf.createServerSocket(port);
			((SSLServerSocket) ss).setNeedClientAuth(true); // enables client
			// authentication
			new Server(ss);
		} catch (IOException e) {
			System.out.println("Unable to start Server: " + e.getMessage());
			e.printStackTrace();
		}
	}
}