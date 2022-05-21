package uk.ncl.CSC8016.JinzhaoChen.Implementations;

import uk.ncl.CSC8016.JinzhaoChen.BankFacade;
import uk.ncl.CSC8016.JinzhaoChen.Result;
import uk.ncl.CSC8016.JinzhaoChen.Operation;
import uk.ncl.CSC8016.JinzhaoChen.TransactionCommands;
import uk.ncl.CSC8016.JinzhaoChen.Utils.AtomicBigInteger;
import uk.ncl.CSC8016.JinzhaoChen.Utils.AtomicDoubleBalance;
import uk.ncl.CSC8016.JinzhaoChen.Utils.BankLogger;
import uk.ncl.CSC8016.JinzhaoChen.Utils.TimeUtil;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.*;

public class BankFacadeImpl extends BankFacade{
    private final ConcurrentHashMap<String, AtomicDoubleBalance> hashMap;
    private AtomicBigInteger transactionId;
    private CopyOnWriteArrayList<Result> resultList;
    public final static String UncompletedLogFolderDir = "src\\main\\java\\uk\\ncl\\CSC8016\\JinzhaoChen\\Logs\\UncompletedLog\\";

    private final int coreThreadNum;
    private ExecutorService requestPool;
    Semaphore semaphore;

    public BankFacadeImpl(HashMap<String, AtomicDoubleBalance> userIdToTotalInitialAmount ) {
        // 1. prepare for basic info
        super(userIdToTotalInitialAmount);
        if ((userIdToTotalInitialAmount == null)) throw new RuntimeException();
        this.hashMap = new ConcurrentHashMap<>(userIdToTotalInitialAmount);
        this.transactionId = new AtomicBigInteger(BigInteger.ZERO);
        this.resultList = new CopyOnWriteArrayList<>();

        // 2. Thread pool

        this.coreThreadNum = Runtime.getRuntime().availableProcessors(); // cores of CPU
        this.requestPool =  new ThreadPoolExecutor( coreThreadNum,
                coreThreadNum * 2,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>());

        // 3. open a bank server thread: redo the uncompleted task, tracking tasks
        this.bankServer();

        this.semaphore = new Semaphore(coreThreadNum * 2);
    }


    /**
     * Add the result of each Transaction and write on disk by the end of the transaction.
     *
     * @param result
     * @return {@code true}
     * */
    private boolean addResultList(Result result) {
        resultList.add(result);
        BankLogger.writeResultsLogs(resultList);
        return true;
    }

    @Override
    public String StudentID() {
        return "c0094835";
    }

    /**
     * Receiving commands and submitted to thread pool, managing threads number by requestPool
     *
     * @param isWithdraw, isBankServer
     * */
    public void requestHandler(boolean isWithdraw, double amount, String userID) throws ExecutionException, InterruptedException, TimeoutException {
        Future<Result> result = requestPool.submit(()->{

            // 1. Open transaction for the users exist in map, otherwise printout the error and terminate the thread.
            TransactionCommands cmds;
            try {
                cmds = openTransaction(userID).get();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }

            // 2. Implement the operation
            if (isWithdraw)
                cmds.withdrawMoney(amount);
            else
                cmds.payMoneyToAccount(amount);

            // Simulating network delay to check if bank server can redo the operation when resuming after a crush
//            try {
//                TimeUnit.SECONDS.sleep(5);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }

            // 3. Commit
            return cmds.commit();
        } );

        // 4. Add to the result list while the thread ended.
        addResultList(result.get(30,TimeUnit.SECONDS));
    }
    /**
     * provide transaction commands if the user exists in the map,invoked by thread pool.
     *
     * @param userId
     * @return TransactionCommands
     * */
    @Override
    public Optional<TransactionCommands> openTransaction(String userId) {
        if(hashMap.containsKey(userId)) {
            return Optional.of(new TransactionCommands(){
                BigInteger currentTransactionId;

                // Transaction status initialised
                boolean isProcessDone, isProcessAborted, isProcessCommitted;

                // used for comparison when commit
                AtomicDoubleBalance beginBalance;

                // save the amount changed temporarily
                Double totalLocalOperations;

                // record operations, stored in disk as well
                ArrayList<Operation> journal;

                Result result;
                BankLogger log;

                {
                    currentTransactionId = transactionId.incrementAndGet();
                    isProcessDone = isProcessAborted = isProcessCommitted = false;
                    beginBalance = hashMap.get(userId);
                    totalLocalOperations = 0.0;

                    journal = new ArrayList<>();
                    result = new Result(currentTransactionId, userId, journal, new ArrayList<>(), beginBalance.getValue(), 1);
                    log = new BankLogger(currentTransactionId, result);
                }
                @Override
                public BigInteger getTransactionId() {
                    return currentTransactionId;
                }
                @Override
                public Double getTentativeTotalAmount() {
                    if (isProcessDone)
                        return hashMap.get(userId).getValue();
                    else
                        return -1.0;
                }
                private boolean validationBeforePayAndWithdraw(double amount){
                    if (amount < 0) {
                        log.lessThanZero(true);
                        return false;
                    }
                    else if (isProcessDone){
                        log.processHasBeenDone();
                        return false;
                    }
                    return true;
                }
                private double getBalanceInAccount(String userId){
                    return hashMap.get(userId).getValue();
                }

                @Override
                public boolean payMoneyToAccount(double amount) {
                    boolean validation = validationBeforePayAndWithdraw(amount);
                    if(!validation) return validation;
                    else {
                        journal.add(Operation.Pay(amount, journal.size()));
                        totalLocalOperations += amount;
                        log.operatedLocally(true, amount, getBalanceInAccount(userId));
                        return true;
                    }
                }

                @Override
                public boolean withdrawMoney(double amount) {
                    boolean validation = validationBeforePayAndWithdraw(amount);
                    if(!validation) return validation;
                    else {
                        journal.add(Operation.Withdraw(amount, journal.size()));
                        totalLocalOperations -= amount;
                        log.operatedLocally(false, amount, getBalanceInAccount(userId));
                        return true;
                    }
                }

                public boolean redoOperations(double amount) {
                    journal.add(Operation.Redo(amount, journal.size()));
                    totalLocalOperations += amount;
                    log.operatedLocally(false, amount, getBalanceInAccount(userId));
                    return true;
                }

                @Override
                public Result commit() {

                    if (!isProcessDone) {
                        isProcessAborted = false;
                        isProcessDone = isProcessCommitted = true;

                        journal.add(Operation.Commit(journal.size()));

                        // if overdraft
                        if (!beginBalance.incrementAndGet(totalLocalOperations)) {

                            // update value and status of transaction fail in result
                            result.setBalance(getBalanceInAccount(userId));
                            result.setStatus(-1);

                            log.overdraft(getBalanceInAccount(userId));
                            log.moveLogWhenCompleted();
                            return result;
                        }
                        else {
                            // update value in result
                            result.setBalance(getBalanceInAccount(userId));

                            log.commitSuccessful(totalLocalOperations, hashMap.get(userId).getValue());
                            log.moveLogWhenCompleted();
                            return result;
                        }
                    } else {
                        return null;
                    }
                }

                @Override
                public Result abort() {
                    if (!isProcessDone) {

                        isProcessDone = isProcessAborted = true;
                        isProcessCommitted = false;
                        journal.add(Operation.Abort(journal.size()));

                        log.transactionAbort();
                        log.moveLogWhenCompleted();

                        // update value and status of transaction fail in result
                        result.setBalance(hashMap.get(userId).getValue());
                        result.setStatus(0);
                        // exchange operations and ignoreOperations
                        result.setIgnoredOperations(result.getOperations());
                        result.setOperations(new ArrayList<>());
                        return result;
                    } else {
                        return null;
                    }
                }
            });
        } else
            return Optional.empty();
    }

    /**
     * open a bank server thread
     * */
    public void bankServer() {
        requestPool.submit(()->{
            // find if there is any uncompleted task, if yes then redo the operations
            // Loop through and add the results to resultList
            for (Result r : this.initialiser()) {
                addResultList(r);
            }
            // print out the info of threads each second
            for (;;) {
                this.heartBeat();
                TimeUnit.SECONDS.sleep(1);
            }
        });
    }
    /**
     * Redo the uncompleted tasks by reading logs file in "Logs\UncompletedLog" and return the results
     *
     * @return ArrayList<Result>
     * */
    public ArrayList<Result> initialiser(){
        ArrayList<Result> taskResultArr = new ArrayList<>();

        // 1. read all log file of uncompleted tasks
        File UncompletedLogFolder = new File(UncompletedLogFolderDir);
        File[] files = UncompletedLogFolder.listFiles();

        // 2. loop through and redo the uncompleted tasks
        if (files.length > 0){
            for (File UncompletedLog: files) {
                try{
                    // redo the operation and add the result to the Array
                    taskResultArr.add(redo(BankLogger.readUncompletedTask(UncompletedLog)));
                    // delete the uncompletedTask file
                    UncompletedLog.delete();
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
        return taskResultArr;
    }
    /**
     * Redo single uncompleted task by recommit the cumulative changed amount of the operation list
     *
     * @param uncompletedTask
     * @return ArrayList<Result>
     * */
    private Result redo(Result uncompletedTask){
        // 1. Reopen a transaction for each unfinished task
        TransactionCommands cmds = openTransaction(uncompletedTask.getUserId()).get();

        // 2. Redo the operations
        double totalLocalOperations = Operation.cumulative(uncompletedTask.getOperations());
        cmds.redoOperations(totalLocalOperations);

        // 3. recommit
        return cmds.commit();
    }
    /**
     * Visualise the information of the thread pool
    * */
    public void heartBeat(){
        ThreadPoolExecutor executor = ((ThreadPoolExecutor) requestPool);
        String msg = TimeUtil.getFullTime() +
                ", pool size: " + executor.getPoolSize() +
                ", active threads: " + executor.getActiveCount() +
                ", waiting in queue: " + executor.getQueue().size() +
                ", completed task: " + executor.getCompletedTaskCount() +
                ", total: " + executor.getTaskCount();
        System.out.println(msg);
    }
}
