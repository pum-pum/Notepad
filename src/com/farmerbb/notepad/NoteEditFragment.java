/* Copyright 2014 Braden Farmer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.farmerbb.notepad;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class NoteEditFragment extends Fragment {
	
	private EditText noteContents;
	String filename = String.valueOf(System.currentTimeMillis());
	String contentsOnLoad = "";
	int length = 0;
	long draftName;
	boolean isSavedNote = false;
	String contents;

    // Receiver used to close fragment when a note is deleted
    public class DeleteNotesReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String[] filesToDelete = intent.getStringArrayExtra("files");

            for(Object file : filesToDelete) {
                if(filename.equals(file)) {
                    // Hide soft keyboard
                    EditText editText = (EditText) getActivity().findViewById(R.id.editText1);
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);

                    // Add NoteListFragment
                    getFragmentManager()
                            .beginTransaction()
                            .replace(R.id.noteViewEdit, new NoteListFragment(), "NoteListFragment")
                            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                            .commit();
                }
            }
        }
    }

    IntentFilter filter = new IntentFilter("com.farmerbb.notepad.DELETE_NOTES");
    DeleteNotesReceiver receiver = new DeleteNotesReceiver();

	/* The activity that creates an instance of this fragment must
	 * implement this interface in order to receive event call backs. */
	public interface Listener {
		public void showBackButtonDialog(String filename);
		public void showDeleteDialog();
		public void showSaveButtonDialog();
		public boolean isShareIntent();
	}

	// Use this instance of the interface to deliver action events
	Listener listener;

	// Override the Fragment.onAttach() method to instantiate the Listener
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		// Verify that the host activity implements the callback interface
		try {
			// Instantiate the Listener so we can send events to the host
			listener = (Listener) activity;
		} catch (ClassCastException e) {
			// The activity doesn't implement the interface, throw exception
			throw new ClassCastException(activity.toString()
										 + " must implement Listener");
		}
	}	
	
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_note_edit, container, false);
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

        // Set values
        setRetainInstance(true);
		setHasOptionsMenu(true);

		// Set up content view
		noteContents = (EditText) getActivity().findViewById(R.id.editText1);

		// Get filename
		try {
			if(getArguments().getString("filename") != "new") {
			    filename = getArguments().getString("filename");
			    if(!filename.equals("draft"))
				    isSavedNote = true;
		    }
		} catch (NullPointerException e) {
			filename = "new";
		}

		// Show the Up button in the action bar.
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);

		// Load note from existing file
		if(isSavedNote) {
			try {
				noteContents.setText(loadNote(filename));
				noteContents.setSelection(length, length);
			} catch (IOException e) {
				showToast(R.string.error_loading_note);
                finish(null);
			}		
		} else if(filename.equals("draft")) {
            SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
                noteContents.setText(sharedPref.getString("draft-contents", null));
                noteContents.setSelection(length, length);
        }

        // Show soft keyboard
		EditText editText = (EditText) getActivity().findViewById(R.id.editText1);
		InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
									
	}

	@Override
	public void onPause() {
		super.onPause();

		// Disable saving drafts if user launched Notepad through a share intent
		if(!listener.isShareIntent() && !isRemoving()) {
            // Set current note contents to a String
            noteContents = (EditText) getActivity().findViewById(R.id.editText1);
            contents = noteContents.getText().toString();

            if(!contents.equals("")) {
                SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);

                // Save filename to draft-name preference
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putLong("draft-name", Long.parseLong(filename));
                editor.putBoolean("is-saved-note", isSavedNote);

                // Write draft to SharedPreferences
                editor.putString("draft-contents", contents);
                editor.apply();

                // Show toast notification
                showToast(R.string.draft_saved);
            }
		}
	}

	@Override
	public void onResume() {
		super.onResume();

        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);

		// Disable restoring drafts if user launched Notepad through a share intent
		if(!listener.isShareIntent()) {
            if(filename.equals("draft")) {

                // Restore draft preferences
                draftName = sharedPref.getLong("draft-name", 0);
                isSavedNote = sharedPref.getBoolean("is-saved-note", false);

                // Restore filename of draft
                filename = Long.toString(draftName);

                // Reload old file into memory, so that correct contentsOnLoad is set
                if(isSavedNote) {
                    try {
                        loadNote(filename);
                    } catch (IOException e) {
                        showToast(R.string.error_loading_note);
                        finish(null);
                    }
                } else
                    contentsOnLoad = "";

                // Notify the user that a draft has been restored
                showToast(R.string.draft_restored);
            }

            // Clear draft preferences
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.remove("draft-name");
            editor.remove("is-saved-note");
            editor.remove("draft-contents");
            editor.apply();
        }

		// Change window title
		if(isSavedNote)
            getActivity().setTitle(getResources().getString(R.string.edit_note));
		else
			getActivity().setTitle(getResources().getString(R.string.action_new));
	}

    // Register and unregister DeleteNotesReceiver
    @Override
    public void onStart() {
        super.onStart();

        if(!listener.isShareIntent())
            getActivity().registerReceiver(receiver, filter);
    }

    @Override
    public void onStop() {
        super.onStop();

        if(!listener.isShareIntent())
            getActivity().unregisterReceiver(receiver);
    }
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.note_edit, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
        // Hide soft keyboard
		EditText editText = (EditText) getActivity().findViewById(R.id.editText1);
		InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
		
		switch (item.getItemId()) {
			case android.R.id.home:
				// Override default Android "up" behavior to instead mimic the back button
				getActivity().onBackPressed();
				return true;

				// Save button
			case R.id.action_save:
				// Get current note contents from EditText
				noteContents = (EditText) getActivity().findViewById(R.id.editText1);
				contents = noteContents.getText().toString();

				// If EditText is empty, show toast informing user to enter some text
				if(contents.equals(""))
					showToast(R.string.empty_note);
				else {
					// If no changes were made since last save, switch back to NoteViewFragment
					if(contentsOnLoad.equals(noteContents.getText().toString())) {
                        Bundle bundle = new Bundle();
                        bundle.putString("filename", filename);

                        Fragment fragment = new NoteViewFragment();
                        fragment.setArguments(bundle);

                        getFragmentManager()
                                .beginTransaction()
                                .replace(R.id.noteViewEdit, fragment, "NoteViewFragment")
                                .commit();
					} else {
						SharedPreferences pref = getActivity().getSharedPreferences(getActivity().getApplicationContext().getPackageName() + "_preferences", Context.MODE_PRIVATE);
						if(pref.getBoolean("show_dialogs", true)) {
							// Show save button dialog
							listener.showSaveButtonDialog();
						} else {
							try {
								saveNote();

								if(listener.isShareIntent())
									getActivity().finish();
								else {
                                    Bundle bundle = new Bundle();
                                    bundle.putString("filename", filename);

                                    Fragment fragment = new NoteViewFragment();
                                    fragment.setArguments(bundle);

                                    getFragmentManager()
                                            .beginTransaction()
                                            .replace(R.id.noteViewEdit, fragment, "NoteViewFragment")
                                            .commit();
								}
							} catch (IOException e) {
								// Show error message as toast if file fails to save
								showToast(R.string.failed_to_save);
							}
						}
					}
				}
				return true;

				// Delete button
			case R.id.action_delete:
				listener.showDeleteDialog();
				return true;

				// Share menu item
			case R.id.action_share:
				// Set current note contents to a String
				contents = noteContents.getText().toString();

				// If EditText is empty, show toast informing user to enter some text
				if(contents.equals(""))
					showToast(R.string.empty_note);
				else {
					// Send a share intent
					Intent intent = new Intent();
					intent.setAction(Intent.ACTION_SEND);
					intent.putExtra(Intent.EXTRA_TEXT, contents);
					intent.setType("text/plain");

					// Verify that the intent will resolve to an activity, and send
					if(intent.resolveActivity(getActivity().getPackageManager()) != null)
						startActivity(Intent.createChooser(intent, getResources().getText(R.string.send_to)));
				}

				return true;             
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	private void deleteNote(String filename) {		
		// Build the pathname to delete file, then perform delete operation
		File fileToDelete = new File(getActivity().getFilesDir() + "/" + filename);
		fileToDelete.delete();
	}

	// Loads note from /data/data/com.farmerbb.notepad/files
	private StringBuffer loadNote(String filename) throws IOException {

		// Initialize StringBuffer which will contain note
		StringBuffer note = new StringBuffer("");

		// Open the file on disk
		FileInputStream input = getActivity().openFileInput(filename);
		InputStreamReader reader = new InputStreamReader(input);
		BufferedReader buffer = new BufferedReader(reader);

		// Load the file
		String line = buffer.readLine();		
		while (line != null ) {
			note.append(line);
			line = buffer.readLine();
			if(line != null)
				note.append("\n");
		}

		// Close file on disk
		reader.close();

		// Write contents to variable to compare when discarding changes
		contentsOnLoad = note.toString();
		length = contentsOnLoad.length();

		return(note);		
	}

	// Saves notes to /data/data/com.farmerbb.notepad/files
	private void saveNote() throws IOException {
		// Set current note contents to a String
		noteContents = (EditText) getActivity().findViewById(R.id.editText1);
		contents = noteContents.getText().toString(); 

		// Write the String to a new file with filename of current milliseconds of Unix time
		if(contents.equals("") && filename.equals("draft"))
			finish(null);
		else {

			// Set a new filename if this is not a draft
			String newFilename;
			if(filename.equals("draft"))
				newFilename = filename;
			else 
				newFilename = String.valueOf(System.currentTimeMillis());

			// Write note to disk
			FileOutputStream output = getActivity().openFileOutput(newFilename, Context.MODE_PRIVATE);
			output.write(contents.getBytes());
			output.close();

			// Delete old file
			if(!filename.equals("draft"))
				deleteNote(filename);

			// Show toast notification
			if(filename.equals("draft"))
				showToast(R.string.draft_saved);
			else
				showToast(R.string.note_saved);

			// Old file is no more
			if(!filename.equals("draft")) {
				filename = newFilename;
				contentsOnLoad = contents;
				length = contentsOnLoad.length();
			}
			
			// Send broadcast to MainActivity to refresh list of notes
			Intent listNotesIntent = new Intent();
			listNotesIntent.setAction("com.farmerbb.notepad.LIST_NOTES");
			getActivity().sendBroadcast(listNotesIntent);
		}
	}

	// Method used to generate toast notifications
    private void showToast(int message) {
        Toast toast = Toast.makeText(getActivity().getApplicationContext(), getResources().getString(message), Toast.LENGTH_SHORT);
        toast.show();
    }

	public void onBackDialogNegativeClick(String filename) {
		// User touched the dialog's negative button
		showToast(R.string.changes_discarded);
		finish(filename);
	}

	public void onBackDialogPositiveClick(String filename) {
		// User touched the dialog's positive button
		try {
			saveNote();
			finish(filename);
		} catch (IOException e) {
			// Show error message as toast if file fails to save
			showToast(R.string.failed_to_save);
		}
	}

	public void onDeleteDialogPositiveClick() {
		// User touched the dialog's positive button
		deleteNote(filename);
		showToast(R.string.note_deleted);
		finish(null);
	}

	public void onSaveDialogNegativeClick() {
		// User touched the dialog's negative button
		if(isSavedNote) {
			showToast(R.string.changes_discarded);

            Bundle bundle = new Bundle();
            bundle.putString("filename", filename);

            Fragment fragment = new NoteViewFragment();
            fragment.setArguments(bundle);

            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.noteViewEdit, fragment, "NoteViewFragment")
                    .commit();
		} else {
			showToast(R.string.changes_discarded);
			finish(null);
		}
	}

	public void onSaveDialogPositiveClick() {
		// User touched the dialog's positive button
		try {
			saveNote();

            if(listener.isShareIntent())
				finish(null);
            else {
                Bundle bundle = new Bundle();
                bundle.putString("filename", filename);

                Fragment fragment = new NoteViewFragment();
                fragment.setArguments(bundle);

                getFragmentManager()
                        .beginTransaction()
                        .replace(R.id.noteViewEdit, fragment, "NoteViewFragment")
                        .commit();
            }
		} catch (IOException e) {
			// Show error message as toast if file fails to save
			showToast(R.string.failed_to_save);
		}
	}
	
	public void onBackPressed(String filename) {
		// Pop back stack if no changes were made since last save
		if(contentsOnLoad.equals(noteContents.getText().toString())) {
			finish(filename);
		} else {
			// Finish if EditText is empty
			if(noteContents.getText().toString().isEmpty())
				finish(filename);
			else {
				SharedPreferences pref = getActivity().getSharedPreferences(getActivity().getApplicationContext().getPackageName() + "_preferences", Context.MODE_PRIVATE);
				if(pref.getBoolean("show_dialogs", true)) {
					// Show back button dialog
					listener.showBackButtonDialog(filename);
				} else {
					try {
						saveNote();
						finish(filename);
					} catch (IOException e) {
						// Show error message as toast if file fails to save
						showToast(R.string.failed_to_save);
					}
				}
			}
		}
	}
	
	public void dispatchKeyShortcutEvent(int keyCode) {		
		switch(keyCode) {

				// CTRL+S: Save
			case KeyEvent.KEYCODE_S:
				// Set current note contents to a String
				contents = noteContents.getText().toString();

				// If EditText is empty, show toast informing user to enter some text
				if(contents.equals(""))
					showToast(R.string.empty_note);
				else {
					try {
						// Keyboard shortcut just saves the note; no dialog shown
						saveNote();
					} catch (IOException e) {
						// Show error message as toast if file fails to save
						showToast(R.string.failed_to_save);
					}
				}
				break;

				// CTRL+D: Delete
			case KeyEvent.KEYCODE_D:
				listener.showDeleteDialog();
				break;

				// CTRL+H: Share
			case KeyEvent.KEYCODE_H:
				// Set current note contents to a String
				contents = noteContents.getText().toString();

				// If EditText is empty, show toast informing user to enter some text
				if(contents.equals(""))
					showToast(R.string.empty_note);
				else {
					// Send a share intent
					Intent shareIntent = new Intent();
					shareIntent.setAction(Intent.ACTION_SEND);
					shareIntent.putExtra(Intent.EXTRA_TEXT, contents);
					shareIntent.setType("text/plain");

					// Verify that the intent will resolve to an activity, and send
					if(shareIntent.resolveActivity(getActivity().getPackageManager()) != null)
						startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.send_to)));
				}
				break;
		}
	}
	
	private void finish(String filename) {
		if(listener.isShareIntent())
			getActivity().finish();
		else if(filename == null) {
		// Add NoteListFragment
		getFragmentManager()
			.beginTransaction()
				.replace(R.id.noteViewEdit, new NoteListFragment(), "NoteListFragment")
			.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
			.commit();
		} else {
			Bundle bundle = new Bundle();
			bundle.putString("filename", filename);

			Fragment fragment = new NoteViewFragment();
			fragment.setArguments(bundle);

			// Add NoteViewFragment
			getFragmentManager()
                .beginTransaction()
                .replace(R.id.noteViewEdit, fragment, "NoteViewFragment")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit();
		}
	}
	
	public void switchNotes(String filename) {
		// Hide soft keyboard
		EditText editText = (EditText) getActivity().findViewById(R.id.editText1);
		InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
		
		// Act as if the back button was pressed
		onBackPressed(filename);
	}
	
	public String getFilename() {
		return filename;
	}
}
