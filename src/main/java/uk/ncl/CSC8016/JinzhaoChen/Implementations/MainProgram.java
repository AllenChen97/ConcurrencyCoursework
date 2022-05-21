package uk.ncl.CSC8016.JinzhaoChen.Implementations;

import uk.ncl.CSC8016.JinzhaoChen.Implementations.BankFacadeImpl;
import uk.ncl.CSC8016.JinzhaoChen.Utils.AtomicDoubleBalance;

import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class MainProgram {
    public static void main(String[] args) throws ExecutionException, InterruptedException, TimeoutException {
        // Initialised the bank instance by creating 3 users in a Hashmap
        HashMap<String, AtomicDoubleBalance> userIdToTotalInitialAmount = new HashMap<>();
        userIdToTotalInitialAmount.put("userA", new AtomicDoubleBalance(100.0));
        userIdToTotalInitialAmount.put("userB", new AtomicDoubleBalance(500.0));
        userIdToTotalInitialAmount.put("userC", new AtomicDoubleBalance(2000.0));
        BankFacadeImpl bank = new BankFacadeImpl(userIdToTotalInitialAmount);


//        // Simulating UserA operating his/her account from two devices
        bank.requestHandler(true,80,"userA");
        bank.requestHandler(true,80,"userA");

        bank.requestHandler(false,50,"userA");
        bank.requestHandler(false,50,"userA");

//        // Simulating multiple user operating there own account
        bank.requestHandler(true,300,"userB");
        bank.requestHandler(true,1200,"userC");
//
//        // UserD does not exist, throw exception
        bank.requestHandler(true,1000000,"userD");

        // test for high volumn request
//        for(;;){
//            bank.requestHandler(false,60,"userA");
//            bank.requestHandler(true,80,"userA");
//        }

    }
}
