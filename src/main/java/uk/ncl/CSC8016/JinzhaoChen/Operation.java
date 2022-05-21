package uk.ncl.CSC8016.JinzhaoChen;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static uk.ncl.CSC8016.JinzhaoChen.OperationType.*;


public class Operation implements Comparable<Operation>, Serializable {

    OperationType type;
    double amount;
    Integer time;

    public OperationType getType() {
        return type;
    }

    public void setT(OperationType type) {
        this.type = type;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    private Operation(Integer t) {
        assert t != null;
        time = t;
    }

    public static Operation Redo(double amount, Integer t) {
        assert amount >= 0;
        Operation op = new Operation(t);
        op.amount = amount;
        op.type = Redo;
        return op;
    }


    public static Operation Pay(double amount, Integer t) {
        assert amount >= 0;
        Operation op = new Operation(t);
        op.amount = amount;
        op.type = Pay;
        return op;
    }


    public static Operation Withdraw(double amount, Integer t) {
        assert amount >= 0;
        Operation op = new Operation(t);
        op.amount = amount;
        op.type = Withdraw;
        return op;
    }


    public static Operation Abort(Integer t) {
        Operation op = new Operation(t);
        op.amount = 0;
        op.type = Abort;
        return op;
    }

    public static Operation Commit(Integer t) {
        Operation op = new Operation(t);
        op.amount = 0;
        op.type = Commit;
        return op;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Operation operation = (Operation) o;
        return Double.compare(operation.amount, amount) == 0 && type == operation.type && Objects.equals(time, operation.time);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, amount, time);
    }

    @Override
    public int compareTo(Operation o) {
        if (o == null) return 1;
        return time.compareTo(o.time);
    }

    public static double cumulative(List<Operation> collection) {
        Collections.sort(collection);
        List<Operation> finalOperations = new ArrayList<>(collection.size());
        double totalAmount = 0;
        for (Operation x : collection) {
            switch (x.type) {
                case Pay:
                    totalAmount += x.amount;
                case Withdraw:
                    totalAmount -= x.amount;
                default:
            }
        }
        return totalAmount; // The operation was neither committed nor aborted. This should be like if nothing happened
    }

    @Override
    public String toString() {
        return "Operation{" +
                "type=" + type +
                ", amount=" + amount +
                ", time=" + time +
                '}';
    }
}
