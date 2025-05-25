package com.example.osmparsing.algorithms;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class IndexMinPQ<Key extends Comparable<Key>> implements Iterable<Integer> {
    private int maxN;        // maximum number of elements allowed
    private int n;           // current number of elements
    private int[] pq;        // binary heap using 1-based indexing (stores indices)
    private int[] qp;        // inverse: qp[i] gives the position of i in pq; -1 if not present
    private Key[] keys;      // keys[i] = priority of i

    // Constructor: initializes the priority queue with capacity maxN
    public IndexMinPQ(int maxN) {
        if (maxN < 0) throw new IllegalArgumentException();
        this.maxN = maxN;
        n = 0;
        keys = (Key[]) new Comparable[maxN + 1];
        pq   = new int[maxN + 1];
        qp   = new int[maxN + 1];
        for (int i = 0; i <= maxN; i++)
            qp[i] = -1;
    }

    // Check if the priority queue is empty
    public boolean isEmpty() {
        return n == 0;
    }

    // Check if index i is in the priority queue
    public boolean contains(int i) {
        validateIndex(i);
        return qp[i] != -1;
    }

    // Return the number of keys in the priority queue
    public int size() {
        return n;
    }

    // Insert key with associated index i
    public void insert(int i, Key key) {
        validateIndex(i);
        if (contains(i)) throw new IllegalArgumentException("index is already in the priority queue");
        n++;
        qp[i] = n;
        pq[n] = i;
        keys[i] = key;
        swim(n);
    }

    // Return the index with the minimum key
    public int minIndex() {
        if (n == 0) throw new NoSuchElementException("Priority queue underflow");
        return pq[1];
    }

    // Return the minimum key
    public Key minKey() {
        if (n == 0) throw new NoSuchElementException("Priority queue underflow");
        return keys[pq[1]];
    }

    // Remove and return the index associated with the minimum key
    public int delMin() {
        if (n == 0) throw new NoSuchElementException("Priority queue underflow");
        int min = pq[1];
        exch(1, n--);
        sink(1);
        assert min == pq[n+1];
        qp[min] = -1;        // mark as deleted
        keys[min] = null;    // help garbage collection
        pq[n+1] = -1;        // not needed
        return min;
    }

    // Return the key associated with index i
    public Key keyOf(int i) {
        validateIndex(i);
        if (!contains(i)) throw new NoSuchElementException("index is not in the priority queue");
        return keys[i];
    }

    // Change the key associated with index i
    public void changeKey(int i, Key key) {
        validateIndex(i);
        if (!contains(i)) throw new NoSuchElementException("index is not in the priority queue");
        keys[i] = key;
        swim(qp[i]);
        sink(qp[i]);
    }

    // Deprecated change method (calls changeKey)
    @Deprecated
    public void change(int i, Key key) {
        changeKey(i, key);
    }

    // Decrease the key associated with index i
    public void decreaseKey(int i, Key key) {
        validateIndex(i);
        if (!contains(i)) throw new NoSuchElementException("index is not in the priority queue");
        if (keys[i].compareTo(key) == 0)
            throw new IllegalArgumentException("Calling decreaseKey() with a key equal to the key in the priority queue");
        if (keys[i].compareTo(key) < 0)
            throw new IllegalArgumentException("Calling decreaseKey() with a key strictly greater than the key in the priority queue");
        keys[i] = key;
        swim(qp[i]);
    }

    // Increase the key associated with index i
    public void increaseKey(int i, Key key) {
        validateIndex(i);
        if (!contains(i)) throw new NoSuchElementException("index is not in the priority queue");
        if (keys[i].compareTo(key) == 0)
            throw new IllegalArgumentException("Calling increaseKey() with a key equal to the key in the priority queue");
        if (keys[i].compareTo(key) > 0)
            throw new IllegalArgumentException("Calling increaseKey() with a key strictly less than the key in the priority queue");
        keys[i] = key;
        sink(qp[i]);
    }

    // Remove the key associated with index i
    public void delete(int i) {
        validateIndex(i);
        if (!contains(i)) throw new NoSuchElementException("index is not in the priority queue");
        int index = qp[i];
        exch(index, n--);
        swim(index);
        sink(index);
        keys[i] = null;
        qp[i] = -1;
    }

    // Validate that index i is within bounds
    private void validateIndex(int i) {
        if (i < 0) throw new IllegalArgumentException("index is negative: " + i);
        if (i >= maxN) throw new IllegalArgumentException("index >= capacity: " + i);
    }

    // Compare keys at positions i and j in the heap array
    private boolean greater(int i, int j) {
        return keys[pq[i]].compareTo(keys[pq[j]]) > 0;
    }

    // Exchange elements in the heap and update the inverse mapping
    private void exch(int i, int j) {
        int swap = pq[i];
        pq[i] = pq[j];
        pq[j] = swap;
        qp[pq[i]] = i;
        qp[pq[j]] = j;
    }

    // Restore heap order by swimming up from position k
    private void swim(int k) {
        while (k > 1 && greater(k/2, k)) {
            exch(k, k/2);
            k = k/2;
        }
    }

    // Restore heap order by sinking down from position k
    private void sink(int k) {
        while (2*k <= n) {
            int j = 2*k;
            if (j < n && greater(j, j+1)) j++;
            if (!greater(k, j)) break;
            exch(k, j);
            k = j;
        }
    }

    // Return an iterator that iterates over indices in ascending order of keys
    public Iterator<Integer> iterator() {
        return new HeapIterator();
    }

    private class HeapIterator implements Iterator<Integer> {
        // Create a copy of the priority queue for iteration
        private IndexMinPQ<Key> copy;

        public HeapIterator() {
            copy = new IndexMinPQ<Key>(pq.length - 1);
            for (int i = 1; i <= n; i++)
                copy.insert(pq[i], keys[pq[i]]);
        }

        public boolean hasNext() {
            return !copy.isEmpty();
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
        public Integer next() {
            if (!hasNext()) throw new NoSuchElementException();
            return copy.delMin();
        }
    }

}
