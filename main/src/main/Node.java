package main;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import genius.core.bidding.BidDetails;

public class Node {
	Double noVisits;
	Double score;
	BidDetails bid;
	Node parent;
	List<Node> children;
	
	public Node() {
		noVisits = 0.0;
		score = 0.0;
		parent = null;
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
	
	public void setNoVisits(Double noVisits) {
		this.noVisits = noVisits;
	}
	
	public void setParent(Node parent) {
		this.parent = parent;
	}
	
	public void setScore(Double score) {
		this.score = score;
	}
	
	public void setBid(BidDetails bid) {
		this.bid = bid;
	}
	
	public Node getRandomChild() {
		Random rand = new Random();
		return children.get(rand.nextInt(children.size()));
	}
	
	 public Node getBestChild() {
	        return Collections.max(this.children, Comparator.comparing(c -> {
	            return c.getNoVisits();
	        }));
	    }
}
