import java.util.PriorityQueue;

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

//		while (true){
//			int val = in.readBits(BITS_PER_WORD);
//			if (val == -1) break;
//			out.writeBits(BITS_PER_WORD, val);
//		}
//		out.close();
		
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}
	
	/**
	 * create an array called counts that tracks how many times a character appears in a file
	 * we do this by reading in 8 bits at a time
	 * we also set counts[PSEUDO_EOF] to 1 manually
	 * @param in
	 * @return
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] counts = new int[ALPH_SIZE+1];
		counts[PSEUDO_EOF] = 1; 
		while(true) {
			int val = in.readBits(BITS_PER_WORD);
			if(val==-1) break;
			counts[val] +=1;
		}
		return counts;
	}
	
	/**
	 * creates a tree using a pq (min heap)
	 * every time a HuffNode is removed from the pq, it is the smallest weight HuffNode at the moment
	 * we connect these to a parent, and build the tree up by adding the HuffNodes we just created to the pq
	 * @param counts
	 * @return
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for(int i = 0; i<counts.length;i++) {
			if(counts[i]>0) {
				pq.add(new HuffNode(i,counts[i],null,null));
			}
		}
		
		while(pq.size()>1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0,left.myWeight+right.myWeight,left,right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	/** 
	 * call recursive helper to return a String array of the encodings
	 * corresponding to each character
	 * @param root
	 * @return
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE+1];
		codingHelper(root, encodings, "");
		return encodings; 
	}
	
	/**
	 * coding helper traverses the tree for all possible paths
	 * all nodes either have two children or no children
	 * when we hit a leaf, manipulate the encodings array
	 * @param root
	 * @param encodings
	 * @param s
	 */
	private void codingHelper(HuffNode root, String[] encodings, String s) {
		if(root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = s;
			return;
		}
		codingHelper(root.myLeft, encodings, s+"0");
		codingHelper(root.myRight, encodings, s+"1");
	}
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
		if(bits!=HUFF_TREE || bits==-1) {
			throw new HuffException("illegal header starts with "+bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}

	/**
	 * use header in file to create tree
	 * if bit==-1, then we throw and exception
	 * if bit==0, we know that it is an internal node-> set left and right children
	 * if bit==1, we know it is a leaf, return node with value, pointing to null and null
	 * @param in
	 * @return
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if(bit==-1) {
			throw new HuffException("the tree is corrupt ");
		}
		if(bit==0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		else {
			int value = in.readBits(BITS_PER_WORD+1);
			return new HuffNode(value,0,null,null);
		}
		
	}
	
	/**
	 * use the tree we just created to write our decompressed file
	 * if bits==-1 we throw an exception and exit the while loop
	 * if bits==0, we know to go left
	 * if bits==1, we know to go right
	 * keep going until we reach a leaf (left and right subchildren are both null)
	 * when we hit a leaf, we write to the output file and reset the current node to the root of the tree
	 * so we can retraverse the tree from the top
	 * @param root
	 * @param in
	 * @param out
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while(true) {
			int bits = in.readBits(1);
			if(bits==-1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if(bits==0) {
					current = current.myLeft;
				}
				else {
					current = current.myRight;
				}
				
				if(current.myLeft==null && current.myRight == null) {
					if(current.myValue == PSEUDO_EOF) {
						break;
					}
					else {
						out.writeBits(BITS_PER_WORD,current.myValue);
						current = root;
					}
				}
			}
		}	
		
	}
	
}