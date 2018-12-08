import java.util.PriorityQueue;
import java.util.TreeMap;

import javax.swing.tree.TreeNode;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] freq = readForCounts(in);
		HuffNode root = makeTreeFromCounts(freq);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}
	
	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE + 1];
		freq[PSEUDO_EOF] = 1;
		
		while (true) {
			int bits = in.readBits(BITS_PER_WORD);
			if (bits == -1) {
				break;
			}
			else {
				// how to map values to int array ??
				freq[bits] = freq[bits] + 1;
			}
		}
		
		return freq;
	}
	
	private HuffNode makeTreeFromCounts(int[] freq) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();

		for(int i = 0; i < freq.length; i++) {
		    if(freq[i] > 0) pq.add(new HuffNode(i,freq[i],null,null));
		}
		
		if(myDebugLevel >= DEBUG_HIGH) System.out.printf("pq created with %d nodes\n", pq.size());

		while (pq.size() > 1) {
		    HuffNode left = pq.remove();
		    HuffNode right = pq.remove();
		    HuffNode t = new HuffNode(-1, left.myWeight+right.myWeight, left, right);
		    pq.add(t);
		}
		HuffNode root = pq.remove();

		return root;
	}
	
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
	    traverse(encodings, root, "");
		return encodings;
	}
	
	public void traverse(String[] encodings, HuffNode tree, String path) {
    	if(tree == null) return;
    	if(tree.myLeft == null && tree.myRight == null) {
    		 encodings[tree.myValue] = path;
    		 if(myDebugLevel >= DEBUG_HIGH) System.out.printf("encoding for %d is %s\n", tree.myValue, path);
    	     return;
    	} else {
    		traverse(encodings, tree.myLeft, path+"0");
    		traverse(encodings, tree.myRight, path+"1");
    	}
    	return;
    }
	
	private void writeHeader(HuffNode root, BitOutputStream out) {
		// write in "HUFF_TREE"
		
		// if the node is a leaf
		if(root.myLeft == null && root.myRight == null) {
			// sort out how to properly write this
			out.writeBits(BITS_PER_WORD+1, 1);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		else {
			out.writeBits(1, 0);
		}
		
		return;
	}
	
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		code = encoding['A'];
		out.writeBits(code.length(), Integer.parseInt(code,2));

		return;
	}
	
	// DECOMPRESSION-RELATED METHODS
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with "+bits);
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}

	//read the tree used to compress
	private HuffNode readTreeHeader(BitInputStream in){
		int bit = in.readBits(1);
		if (bit == -1) throw new HuffException("illegal header starts with "+bit);
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		else {
			int value = in.readBits(BITS_PER_WORD+1);
			return new HuffNode(value,0,null,null);
		}
	}

	// read bits from compressed file and use them to traverse root-to-leaf paths,
	// writing leaf values to the output file. Stop when reaching PSEUDO_EOF
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root; 
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else { 
				if (bits == 0) current = current.myLeft;
				else current = current.myRight;

				if (current.myLeft==null && current.myRight==null) {
					if (current.myValue == PSEUDO_EOF) 
						break;
					else {
							out.writeBits(BITS_PER_WORD, current.myValue);
							current = root; // start back after leaf
						}
				}
			}
		}
	}
}