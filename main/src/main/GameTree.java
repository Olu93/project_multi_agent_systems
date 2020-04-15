package main;

public class GameTree {
	Node root;

	public GameTree(){
		root = new Node();
	}

	public void setRoot(Node root) {
		this.root = root;
	}
	
	public Node getRoot() {
		return root;
	}
}
