package main.helper;

import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;

public class IssueValuePair {
    IssueDiscrete issue;
    ValueDiscrete value;

    public IssueValuePair(Issue issue2, Value value2) {
        this.issue = (IssueDiscrete) issue2;
        this.value = (ValueDiscrete) value2;
    }

    public IssueDiscrete getIssue() {
        return issue;
    }

    public void setIssue(IssueDiscrete issue) {
        this.issue = issue;
    }

    public ValueDiscrete getValue() {
        return value;
    }

    public void setValue(ValueDiscrete value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return issue.getName() + ":" + value.getValue();
    }

    @Override
    public boolean equals(Object obj) {
        // if(obj instanceof IssueValuePair == false) return false;
        // IssueValuePair tmp = (IssueValuePair) obj;
        return this.toString().contentEquals(obj.toString());
    }
    // @Override
    // public int hashCode() {
    // return (this.getIssue().getName() + this.getValue().getValue()).hashCode();
    // }


}