# 一、描述


# 二、假设
1. 每个用户是一个线程，直接暴露 interface，不需要前端
2. 如何 bank 是一个 server，必须有一个公共变量 client message 接受用户请求
3. server 要持续监控并 handle 用户线程是否成功 commit/abort，不需要管服务端的高可用，但得关注客户端的错误
4. BankFacade 被有 account 的用户初始化，不允许新建和删除账号
5. 允许多个用户在不同的账号登录，应满足 optimistic protocols for transactions，但不强求


# 三、要求
1. BankFacade and TransactionCommands 应被继承实现
2. maven项目打包
3. 不允许用有 concurrency control 功能的包，不能用外部server或者处理器
4. 应提供段报告来说明得分点，其中附上源码 和 继承BankFacade and TransactionCommands 的类名
5. 在 classinfo.txt 说明 继承BankFacade and TransactionCommands 的类名


# 四、分数

## 单线程 50%
1. 如果用户不在map里，即没有 bank account，不能 openTransaction - 10% (完成-本来就写好了)

2. 如果用户有bank account，则可 openTransaction，并在commit交易的时候打印totalAmount - 10%  (完成-BankLogger.balanceRemain(double))

3. 在commit交易之后要提供变化的信息，包含以下信息 - 15% (完成，第二点最好优化一下)
   - 至少一个 Operation and OperationType
   - 在支付之后，最后totalAmount = 交易前余额 + 交易金额
   - 不能 recommit 或 abort 一个结束了的交易
   - 一个成功的交易应包含所有操作的输出包括 commit

4. 不允许 overdraft 透支 - 15% (完成-AtomicDouble-CAS)
   - 取出0.0元出来是可以的
   - 不可 withdraw 比余额大的金额
   - 提交应列出所有操作，包括尝试 overdraft
   - 超支是个失败的交易，而不应 be ignored (完成-CommitResult-status)

## 多线程 40%
1. 单个用户可以同时打开多个 transaction - 10% (完成-AtomicDouble-CAS)
   - 允许同一用户用多个设备登录，打开多个线程
   - 每个线程只能从他们账户中 看见已提交的状态
   - 没有write-write conflicts

2. 多个用户可以打开多个transactions，并不是 Serially 排队，当他们在操作多个account - 10% (完成-ConcurrentHashMap)

3. 不允许脏读 - 10%
   - 用户在提交之后，不可以读到其他线程提交之前的金额 (完成)
   - 不可以withdraw 其他线程还没 commit 的 deposit money (完成)
   - Ignored operations 应被视为失败的 withdraws

4. 处理 aborted transaction - 10%
   - aborted transactions 应被完全被 ignored/reversed (完成)
   - 特别是被 aborted 的线程应 consistent，所以 aborted 之后应该还有 further transactions run

## 加分项 20%
1. 银行被模拟成单独的线程 - 1% (完成)
2. 用concurrent collections - 1% (完成)
3. 程序应可视化地判断线程执行是否成功 - 1% (完成)
4. 正确地利用 semaphores - 1%
5. 线程能马上收到 账户余额的更新 (volatile) - 2%
6. 遵循 optimistic transaction principle，并且所有操作记录在日志，(in primary memory or in secondary memory) - 2%
   - [optimistic transaction](https://en.wikipedia.org/wiki/Optimistic_concurrency_control)
7. 用 monitor or 多线程 的producers and consumers (semaphores 或许可以用) - 2%
8. 用线程池处理多个用户请求 - 3%
9. 任何pom导入的包 不违反 3rd Submission Requirement - 3%
10. bank thread 的宕机也被处理，所有交易回撤，bank journal 被保存到磁盘 - 4% (完成)


# 五、解题思路
1. 了解 optimistic transaction 和 3rd Submission Requirement 额外的要求，避免返工
2. 了解server - client的交互，clarify 类的关系
3. 了解ConcurrentHashMap 和 AtomicReference
4. 基本实现用户存取钱的操作 (完成)
   1. 检查是否有bank account
   2. 打印所有的**操作和变化**，并持久化到log日志
   3. 防止透支

5. 考虑多线程问题: 
   1. 不能 synchronize 锁住账户，因为可以从多个设备登录并操作，产生多个线程操作同一账户 (完成)
      1. 写的是否提交前还得检查值是否一样，如果不一样就重新计算，验证的时候和初始读取的账户余额一致则提交；
   2. 所有用户的 transaction 应该在线程中执行，而不是串行的 (完成)
   3. 防止脏读：不能读取到未提交的值 (完成)
   4. 回滚 aborted 任务，会有 further transaction
   
6. 银行server是一个单独线程接受用户请求
   1. 线程池 + monitor 监控线程/ 生产消费者模式（semaphores）
   2. 并且server宕机 让当下所有未提交的交易回撤，journal被保存到磁盘 (完成)

# 六、笔记
## 1. ConcurrentHashMap
1. 多个线程并发向HashMap添加数据时，因导致Entry的next节点用不为空，让操作进入死循环
2. HashTable 用synchronized 保证线程安全，效率低下：当多个线程同步访问时，其他线程阻塞
3. 锁分段：每次更新数据的时候只锁住一部分数据

## 2. Atomic 原子类
### 2.1. 原始Atomic: AtomicBoolean、AtomicInteger 和 AtomicLong

### 2.2. 引用: AtomicReference、AtomicStampedReference 和 AtomicMarkableReference
- 原始 Atomic 不支持的类型 如double bigInteger 这些可以使用Reference来引用
- 使用方法见：AtomicDoubleBalance
- 赋值set，修改compareAndSet

### 2.3. 对象: AtomicIntegerFieldUpdater、AtomicLongFieldUpdater 和 AtomicReferenceFieldUpdater

### 2.4. 累加器: DoubleAccumulator、DoubleAdder、LongAccumulator 和 LongAdder


## 3. 数组: AtomicIntegerArray、AtomicLongArray 和 AtomicReferenceArray

