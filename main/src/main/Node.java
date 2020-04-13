package main;

import java.util.List;

public class Node {
	Double noVisits;
	Double score;
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
	
	public void setNoVisits(Double noVisits) {
		this.noVisits = noVisits;
	}
	
	public void setParent(Node parent) {
		this.parent = parent;
	}
	
	public void setScore(Double score) {
		this.score = score;
	}
}
