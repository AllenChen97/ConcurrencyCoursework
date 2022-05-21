package uk.ncl.CSC8016.JinzhaoChen.Utils;

import uk.ncl.CSC8016.JinzhaoChen.Result;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CopyOnWriteArrayList;

public class BankLogger {

    public final static String logFolder = "src\\main\\java\\uk\\ncl\\CSC8016\\JinzhaoChen\\Logs\\";
    private final String threadName;
    private final BigInteger TransactionID;
    private final String logFileName;
    private final File UncompletedLog;

//    public BufferedWriter writter;
    Result result;

    public BankLogger(BigInteger transactionID, Result result){
        this.TransactionID = transactionID;
        this.threadName = Thread.currentThread().getName();
        this.logFileName = threadName + "_" + transactionID + ".log";
        this.UncompletedLog = new File(logFolder + "UncompletedLog\\" + logFileName);
        this.result = result;
    }

    public static void writeResultsLogs(CopyOnWriteArrayList<Result> transactionMsg){

        File transactionLogs = new File(logFolder + "transactionLogs.log");
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(transactionLogs))) {
            out.writeObject(transactionMsg);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeOperation(){
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(UncompletedLog))) {
            out.writeObject(result);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Result readUncompletedTask(File UncompletedLog) {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(UncompletedLog))) {
            Result result = (Result) in.readObject();
            return result;
        }
        catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void moveLogWhenCompleted(){
        try {
//            writter.close();
            Files.move(Paths.get(logFolder + "UncompletedLog\\" + logFileName),
                    Paths.get(logFolder + logFileName),
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public String getTimeAndThread(){
        return "Time: " + TimeUtil.getFullTime() +
                ", Thread: " + threadName +
                ", TransactionId: " + TransactionID;
    }

    public boolean processHasBeenDone() {
        String msg = getTimeAndThread() + ", Process has been done, operation failed.";
        writeOperation();
        System.out.println(msg);
        return true;
    }
    public boolean lessThanZero(boolean isPayment){
        String msg = getTimeAndThread() +
                (isPayment? ", Payment less": " Withdraw more") +
                " than the zero, transaction failed.";
        writeOperation();
        System.out.println(msg);
        return true;
    }


    public boolean operatedLocally(boolean isPayment, double amount, double balance){
        String msg = getTimeAndThread() +
                (isPayment? ", Paying ": ", Withdrawing ") + amount + ", with balance in account: " + balance;
        writeOperation();
        System.out.println(msg);
        return true;
    }

    public boolean overdraft(double balance){
        try {
            String msg = getTimeAndThread() +
                    ", Withdraw more than the balance, transaction failed" +
                    ", Balance in account: " + balance ;
            writeOperation();
            System.out.println(msg);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean commitSuccessful(double balanceChange, double balance) {
        String msg = getTimeAndThread() + ", Transaction was executed successfully" +
                (balanceChange>0 ? ", Paid ": ", Withdrew ")+ Math.abs(balanceChange) +
                ", Balance in account: " + balance +
                ", transaction closed";
        writeOperation();
        System.out.println(msg);
        return true;
    }

    public boolean transactionAbort() {
        String msg = getTimeAndThread() + ", Transaction was aborted and balance in account would be changed";
        writeOperation();
        System.out.println(msg);
        return true;
    }
}
