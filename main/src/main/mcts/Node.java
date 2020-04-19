package main.mcts;

import genius.core.bidding.BidDetails;
import main.helper.Utils;

import java.util.*;

public class Node {
	Double noVisits;
	Double score;
	BidDetails bid;
	Node parent;
	List<Node> children;
	String id;
	Random rand;

	public Node() {
		rand = new Random();
		noVisits = 0.0;
		score = 0.0;
		parent = null;
		children = new ArrayList<Node>();
		id = rand.nextInt(Integer.MAX_VALUE) + "";
	}

	public List<Node> getChildren() {
		return children;
	}

	public Double getNoVisits() {
		return noVisits;
	}

	public Double getScore() {
		return score;
	}

	public Node getParent() {
		return parent;
	}

	public BidDetails getBid() {
		return bid;
	}

	public String getId() {
		return id + ": "+noVisits+" - "+Utils.round(score, 5)+" - " + bid;
	}

	public void setNoVisits(Double noVisits) {
		this.noVisits = noVisits;
	}

	public Node setParent(Node parent) {
		this.parent = parent;
		return this;
	}

	public Node addChild(Node child) {
		this.children.add(child);
		return this;		
	}

	public void setScore(Double score) {
		this.score = score;
	}

	public Node setBid(BidDetails bid) {
		this.bid = bid;
		return this;
	}

	public Node getRandomChild() {
		Random rand = new Random();
		return children.get(rand.nextInt(children.size()));
	}

	public Node getBestChild() {
		return 	Collections.max(this.children, Comparator.comparing(c -> {
			return c.getScore();
		}));
	}

	public String toString() {
		StringBuilder buffer = new StringBuilder(50);
		print(buffer, "", "");
		return buffer.toString();
	}

	private void print(StringBuilder buffer, String prefix, String childrenPrefix) {
		buffer.append(prefix);
		buffer.append(this.getId());
		buffer.append('\n');
		for (Iterator<Node> it = children.iterator(); it.hasNext();) {
			Node next = it.next();
			if (it.hasNext()) {
				next.print(buffer, childrenPrefix + "├── ", childrenPrefix + "│   ");
			} else {
				next.print(buffer, childrenPrefix + "└── ", childrenPrefix + "    ");
			}
		}
	}
}
