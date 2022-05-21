package uk.ncl.CSC8016.JinzhaoChen;


import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

public class Result implements Serializable {
    private final BigInteger transactionId;
    private final String userId;
    private List<Operation> operations;
    private List<Operation> ignoredOperations;

    private double balance;
    private int status;  // status: 0_aborted, 1_success, -1_failed

    public Result(BigInteger transactionId, String userId, List<Operation> operations, List<Operation> ignoredOperations, double balance, int status) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.operations = operations;
        this.ignoredOperations = ignoredOperations;
        this.balance = balance;
        this.status = status;
    }


    public String getUserId() {
        return userId;
    }
    public void setBalance(double balance) {
        this.balance = balance;
    }
    public void setStatus(int status) {
        this.status = status;
    }

    public List<Operation> getOperations() {
        return operations;
    }

    public void setOperations(List<Operation> operations) {
        this.operations = operations;
    }

    public List<Operation> getIgnoredOperations() {
        return ignoredOperations;
    }

    public void setIgnoredOperations(List<Operation> ignoredOperations) {
        this.ignoredOperations = ignoredOperations;
    }

    @Override
    public String toString() {
        return  "transactionId=" + transactionId +
                ", operations=" + operations +
                ", balance=" + balance +
                ", status=" + status;
    }
}
