package tfd.pay.common.model;

/**
 * A node's view of an account, with its versions (docs/STACK.md §3.2).
 *
 * @param balance current balance at this node
 * @param nextSeq {@code next[a]}: number of the account's own transfers applied (FIFO version)
 * @param version {@code ver[a]}: number of applied transfers touching the account (debits + credits)
 */
public record AccountView(long balance, long nextSeq, long version) {
    public static final AccountView EMPTY = new AccountView(0, 0, 0);
}
